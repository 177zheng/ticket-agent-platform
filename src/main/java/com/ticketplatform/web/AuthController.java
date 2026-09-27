package com.ticketplatform.web;

import com.ticketplatform.domain.Role;
import com.ticketplatform.domain.User;
import com.ticketplatform.repo.UserRepository;
import com.ticketplatform.security.CurrentUser;
import com.ticketplatform.security.JwtService;
import com.ticketplatform.service.AvatarService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 认证接口：
 *   POST /api/auth/register —— 自助注册，只能创建"员工"角色（role 字段即使传了 ADMIN 也会被忽略）
 *   POST /api/auth/login    —— 登录换取 JWT
 *   GET  /api/auth/me       —— 当前登录人信息
 * 管理员不开放注册，由系统预置（默认 admin/admin123，见 DataSeeder）。
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AvatarService avatarService;

    public AuthController(UserRepository userRepository, PasswordEncoder passwordEncoder,
                          JwtService jwtService, AvatarService avatarService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.avatarService = avatarService;
    }

    public record RegisterRequest(
            @NotBlank @Pattern(regexp = "^[a-zA-Z0-9_]{3,20}$",
                    message = "用户名需 3-20 位字母/数字/下划线")
            String username,
            @NotBlank @Size(max = 50, message = "姓名最长 50 字")
            String name,
            @NotBlank @Email(message = "邮箱格式不正确")
            String email,
            @NotBlank @Size(min = 6, max = 64, message = "密码长度需 6-64 位")
            String password) {
    }

    public record LoginRequest(
            @NotBlank String username,
            @NotBlank String password) {
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> register(@Valid @RequestBody RegisterRequest req) {
        if (userRepository.existsByUsername(req.username())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "用户名已被占用：" + req.username());
        }
        // 关键安全约束：注册永远只能创建员工角色
        User user = new User(req.username(), passwordEncoder.encode(req.password()),
                req.name(), req.email(), Role.EMPLOYEE);
        userRepository.save(user);
        return loginResult(user);
    }

    @PostMapping("/login")
    public Map<String, Object> login(@Valid @RequestBody LoginRequest req) {
        User user = userRepository.findByUsername(req.username())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "用户名或密码错误"));
        if (!passwordEncoder.matches(req.password(), user.getPassword())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "用户名或密码错误");
        }
        return loginResult(user);
    }

    @GetMapping("/me")
    public Map<String, Object> me() {
        String username = CurrentUser.username();
        if (username == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "未登录");
        }
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "用户不存在"));
        return userInfo(user);
    }

    public record ProfileRequest(
            @NotBlank @Size(max = 50, message = "姓名最长 50 字")
            String name,
            @NotBlank @Email(message = "邮箱格式不正确")
            String email) {
    }

    public record PasswordRequest(
            @NotBlank String oldPassword,
            @NotBlank @Size(min = 6, max = 64, message = "新密码长度需 6-64 位")
            String newPassword) {
    }

    /** 修改个人信息：仅姓名/邮箱；用户名与角色不允许自行变更 */
    @PutMapping("/profile")
    public Map<String, Object> updateProfile(@Valid @RequestBody ProfileRequest req) {
        User user = currentUser();
        user.setName(req.name());
        user.setEmail(req.email());
        userRepository.save(user);
        return userInfo(user);
    }

    /** 修改密码：需验证原密码 */
    @PutMapping("/password")
    public Map<String, Object> changePassword(@Valid @RequestBody PasswordRequest req) {
        User user = currentUser();
        if (!passwordEncoder.matches(req.oldPassword(), user.getPassword())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "原密码不正确");
        }
        user.changePassword(passwordEncoder.encode(req.newPassword()));
        userRepository.save(user);
        return Map.of("message", "密码已修改");
    }

    /** 上传头像：multipart 表单，字段名 file；png/jpg/jpeg/gif/webp，≤2MB */
    @PostMapping(value = "/avatar", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> uploadAvatar(@org.springframework.web.bind.annotation.RequestParam("file")
                                            org.springframework.web.multipart.MultipartFile file) {
        User user = currentUser();
        String url = avatarService.save(user.getUsername(), file);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("avatarUrl", url);
        return result;
    }

    private User currentUser() {
        String username = CurrentUser.username();
        if (username == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "未登录");
        }
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "用户不存在"));
    }

    private Map<String, Object> loginResult(User user) {
        Map<String, Object> result = new LinkedHashMap<>(userInfo(user));
        result.put("token", jwtService.issue(user.getUsername(), user.getRole()));
        return result;
    }

    private Map<String, Object> userInfo(User user) {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("username", user.getUsername());
        info.put("name", user.getName());
        info.put("email", user.getEmail());
        info.put("role", user.getRole().name());
        info.put("roleLabel", user.getRole().getLabel());
        info.put("createdAt", user.getCreatedAt().toString());
        info.put("avatarUrl", avatarService.avatarUrl(user.getUsername()));
        return info;
    }
}
