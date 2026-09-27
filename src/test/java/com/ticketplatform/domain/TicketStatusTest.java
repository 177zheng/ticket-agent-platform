package com.ticketplatform.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** 工单状态机：所有合法/非法流转。这里守住的是"非法流转必须被拒绝"这条底线。 */
class TicketStatusTest {

    @Test
    void 主链路_新建到解决_全部合法() {
        assertTrue(TicketStatus.NEW.canTransitionTo(TicketStatus.TRIAGING));
        assertTrue(TicketStatus.TRIAGING.canTransitionTo(TicketStatus.RETRIEVING));
        assertTrue(TicketStatus.RETRIEVING.canTransitionTo(TicketStatus.DRAFTING));
        assertTrue(TicketStatus.DRAFTING.canTransitionTo(TicketStatus.HUMAN_REVIEW));
        assertTrue(TicketStatus.HUMAN_REVIEW.canTransitionTo(TicketStatus.RESOLVED));
    }

    @Test
    void 回复Agent三种去向_全部合法() {
        assertTrue(TicketStatus.DRAFTING.canTransitionTo(TicketStatus.RESOLVED));   // 自动回复
        assertTrue(TicketStatus.DRAFTING.canTransitionTo(TicketStatus.ESCALATED));  // 直接转人工
        assertTrue(TicketStatus.DRAFTING.canTransitionTo(TicketStatus.HUMAN_REVIEW));
    }

    @Test
    void 重开闭环_已解决可转人工_再关回已解决() {
        assertTrue(TicketStatus.RESOLVED.canTransitionTo(TicketStatus.ESCALATED));
        assertTrue(TicketStatus.ESCALATED.canTransitionTo(TicketStatus.RESOLVED));
    }

    @Test
    void 失败态_只能回到三个工作阶段() {
        assertTrue(TicketStatus.FAILED.canTransitionTo(TicketStatus.TRIAGING));
        assertTrue(TicketStatus.FAILED.canTransitionTo(TicketStatus.RETRIEVING));
        assertTrue(TicketStatus.FAILED.canTransitionTo(TicketStatus.DRAFTING));
        assertFalse(TicketStatus.FAILED.canTransitionTo(TicketStatus.RESOLVED));
        assertFalse(TicketStatus.FAILED.canTransitionTo(TicketStatus.NEW));
    }

    @Test
    void 非法跳跃_一律拒绝() {
        assertFalse(TicketStatus.NEW.canTransitionTo(TicketStatus.RESOLVED));      // 不能凭空解决
        assertFalse(TicketStatus.NEW.canTransitionTo(TicketStatus.DRAFTING));      // 不能跳过分类检索
        assertFalse(TicketStatus.HUMAN_REVIEW.canTransitionTo(TicketStatus.DRAFTING)); // 审核态不能回退
        assertFalse(TicketStatus.RESOLVED.canTransitionTo(TicketStatus.TRIAGING));  // 已解决不能重回流水线
        assertFalse(TicketStatus.RESOLVED.canTransitionTo(TicketStatus.NEW));
    }

    @Test
    void 终态判定() {
        assertTrue(TicketStatus.RESOLVED.isTerminal());
        assertFalse(TicketStatus.ESCALATED.isTerminal()); // 转人工还等关闭
        assertFalse(TicketStatus.FAILED.isTerminal());    // 失败可断点重试
    }
}
