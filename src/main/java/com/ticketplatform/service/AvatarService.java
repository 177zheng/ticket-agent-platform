package com.ticketplatform.service;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;

/**
 * 头像存本地文件系统 data/avatars/{username}.{ext}（data 目录已被 gitignore）。
 * 用户名受注册正则约束（字母/数字/下划线），无路径穿越风险。
 */
@Service
public class AvatarService {

    private static final Set<String> ALLOWED_EXT = Set.of("png", "jpg", "jpeg", "gif", "webp");
    private static final long MAX_BYTES = 2 * 1024 * 1024;

    private final Path dir = Paths.get("data", "avatars");

    /** 保存头像并返回可访问 URL（带时间戳防缓存） */
    public String save(String username, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("请选择图片文件");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new IllegalArgumentException("图片不能超过 2MB");
        }
        String ext = extensionOf(file.getOriginalFilename());
        if (!ALLOWED_EXT.contains(ext)) {
            throw new IllegalArgumentException("仅支持 png/jpg/jpeg/gif/webp 格式");
        }
        try {
            Files.createDirectories(dir);
            // 同一用户只保留一个头像：先清掉其他扩展名的旧文件
            for (String e : ALLOWED_EXT) {
                Files.deleteIfExists(dir.resolve(username + "." + e));
            }
            Path target = dir.resolve(username + "." + ext);
            file.transferTo(target.toAbsolutePath());
            return url(target);
        } catch (IOException e) {
            throw new IllegalStateException("头像保存失败: " + e.getMessage(), e);
        }
    }

    /** 已有头像返回 URL，否则 null（前端回退为姓名首字圆圈） */
    public String avatarUrl(String username) {
        for (String e : ALLOWED_EXT) {
            Path p = dir.resolve(username + "." + e);
            if (Files.exists(p)) {
                return url(p);
            }
        }
        return null;
    }

    private String url(Path file) {
        try {
            return "/avatars/" + file.getFileName() + "?t=" + Files.getLastModifiedTime(file).toMillis();
        } catch (IOException e) {
            return "/avatars/" + file.getFileName();
        }
    }

    private static String extensionOf(String filename) {
        if (filename == null) return "";
        int i = filename.lastIndexOf('.');
        return i < 0 ? "" : filename.substring(i + 1).toLowerCase();
    }
}
