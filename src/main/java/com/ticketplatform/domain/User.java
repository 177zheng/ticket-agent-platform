package com.ticketplatform.domain;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * 登录用户。注册接口只允许创建 EMPLOYEE；ADMIN 由种子数据或运维预置。
 */
@Entity
@Table(name = "users",
        uniqueConstraints = @UniqueConstraint(name = "uk_users_username", columnNames = "username"))
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 30)
    private String username;

    /** BCrypt 哈希，永不存明文 */
    @Column(nullable = false, length = 100)
    private String password;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(nullable = false, length = 200)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role = Role.EMPLOYEE;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public User() {
    }

    public User(String username, String passwordHash, String name, String email, Role role) {
        this.username = username;
        this.password = passwordHash;
        this.name = name;
        this.email = email;
        this.role = role;
    }

    public Long getId() { return id; }
    public String getUsername() { return username; }
    public String getPassword() { return password; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public Role getRole() { return role; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    /** 修改密码（传入的是 BCrypt 后的哈希） */
    public void changePassword(String passwordHash) {
        this.password = passwordHash;
    }
}
