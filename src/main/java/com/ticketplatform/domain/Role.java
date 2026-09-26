package com.ticketplatform.domain;

public enum Role {
    /** 普通员工：注册只能创建该角色；可提单、看自己的工单 */
    EMPLOYEE("员工"),
    /** 管理员：系统预置（不开放注册）；全量工单、审核、知识库管理 */
    ADMIN("管理员");

    private final String label;

    Role(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
