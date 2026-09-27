package com.ticketplatform.service;

import com.ticketplatform.domain.Priority;
import com.ticketplatform.domain.Role;
import com.ticketplatform.domain.Ticket;
import com.ticketplatform.domain.TicketStatus;
import com.ticketplatform.domain.User;
import com.ticketplatform.repo.TicketRepository;
import com.ticketplatform.repo.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 首次启动时灌入演示数据：
 * 1) 预置管理员账号（注册接口不开放管理员，只能从这里来）
 * 2) 6 篇运维手册（进知识库切块）+ 6 条历史已解决工单（供检索 Agent 找相似案例）
 * 已有数据则跳过，不会重复导入。
 */
@Component
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final TicketRepository ticketRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final KnowledgeBaseService knowledgeBase;

    public DataSeeder(TicketRepository ticketRepository, UserRepository userRepository,
                      PasswordEncoder passwordEncoder, KnowledgeBaseService knowledgeBase) {
        this.ticketRepository = ticketRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.knowledgeBase = knowledgeBase;
    }

    @Override
    public void run(String... args) {
        if (userRepository.count() == 0) {
            userRepository.save(new User("admin",
                    passwordEncoder.encode("admin123"), "系统管理员", "admin@corp.com", Role.ADMIN));
            log.info("已创建默认管理员 >>>  用户名: admin  密码: admin123（仅演示环境使用）");
        }
        if (ticketRepository.count() > 0) {
            log.info("已有 {} 条工单，跳过工单种子数据", ticketRepository.count());
            return;
        }
        seedKnowledge();
        seedHistoricalTickets();
        log.info("种子数据导入完成：运维手册 + 历史工单");
    }

    private void seedKnowledge() {
        knowledgeBase.ingest("密码重置与账号解锁操作手册", """
                适用范围：AD 域账号、OA 系统、邮箱密码遗忘或账号被锁定。
                一、用户自助重置（首选，5 分钟内完成）：
                1. 打开自助服务门户 https://ssotool.corp（内网可达），点击「忘记密码」；
                2. 输入工号与预留手机号，接收短信验证码；
                3. 设置新密码，需满足复杂度：长度≥10 位，包含大小写字母、数字、特殊字符，且不得与前 3 次密码相同；
                4. 重置成功后 5 分钟内新密码在 AD/OA/邮箱全平台生效，若未生效请先锁屏再解锁刷新凭证缓存。
                二、账号被锁定：连续输错密码 5 次将自动锁定 30 分钟，可在自助门户点击「解锁账号」，验证码通过后立即解锁。
                三、管理员重置（自助失败时）：用户本人电话联系 IT 服务台（内线 8888），工程师核对姓名、工号、部门三项信息后，在 AD 管理控制台执行密码重置，并要求用户首次登录立即修改临时密码。
                四、常见问题：提示「密码已过期」属于正常策略（90 天强制更换），走自助重置即可；提示「账号禁用」多为离职复职流程未走完，需转人事系统处理。""");

        knowledgeBase.ingest("VPN 接入故障排查手册", """
                适用范围：居家/出差办公无法连接公司 VPN，或连接后无法访问内网系统。
                一、无法建立连接（进度条卡住/报超时）：
                1. 确认使用最新版客户端：帮助→检查更新，低于 7.2 的版本必须先升级；
                2. 检查双因素认证：短信验证码 60 秒有效，若收不到验证码，先确认手机号在 HR 系统中登记正确；
                3. 换网络环境测试：手机热点可连、家庭宽带不可连，多为运营商 UDP 限制，客户端设置中切换为 TLS 443 端口模式。
                二、连接成功但访问不了内网（OA/ERP 打不开）：
                1. 客户端「诊断」菜单里点击「重置虚拟网卡」，然后重连；
                2. 命令行执行 ipconfig 查看是否获取到 10.x.x.x 段虚拟 IP，未获取则重装客户端虚拟网卡驱动；
                3. 部分内网系统有访问权限控制，确认已申请对应系统权限（权限申请走 OA「权限工单」流程）。
                三、批量故障（部门内多人同时异常）：不要逐台处理，立即上报网络运维组核查 VPN 网关集群状态，属重大事件按 P2 响应。""");

        knowledgeBase.ingest("打印机故障处理手册", """
                适用范围：办公打印机、复印机、扫描仪常见故障。
                一、卡纸：关机断电后按进纸方向缓慢抽出纸张，切勿反拉；卡纸位置在定影组件（机器后部）时不要自行拆卸，登记设备资产号后联系外包维保（厂商 400 电话见设备贴纸）。
                二、打印乱码或输出空白页：
                1. 先清空打印队列：控制面板→设备和打印机→查看打印内容→取消所有文档；
                2. 删除打印机后重新添加（共享打印机地址打印服务器\\printer\\楼层-区域编号）；
                3. 仍乱码则到官网下载对应型号最新驱动重装，注意区分 32/64 位。
                三、提示「脱机」：多为网络抖动，打印机面板打印配置页核对 IP，与端口配置不一致时改端口或让网络组固定打印机 IP（MAC 绑定）。
                四、耗材更换：碳粉低于 10% 时会邮件提醒，备件在各楼层行政柜登记领取；硒鼓、定影组件等大部件由行政统一报修更换。
                五、扫描件发邮件失败：检查扫描仪 SMTP 配置指向 smtp.corp:465，发件人账号需开通 SMTP 授权。""");

        knowledgeBase.ingest("企业邮箱使用与故障处理手册", """
                适用范围：企业邮箱收发异常、容量告警、客户端配置。
                一、邮箱容量已满（无法收信，发件人收到退信）：
                1. 网页版登录 mail.corp，「设置→存储管理」按大小排序删除大附件邮件；
                2. 清空垃圾箱与已删除（占据隐藏配额）；
                3. 常规配额 20G，因工作需要扩容至 50G/100G 需部门负责人审批，走 OA「邮箱扩容」流程。
                二、收不到外部邮件：
                1. 先让发件方确认是否收到退信，退信代码 550 多为被反垃圾系统拦截，让发件方按退信内申诉地址申请加白；
                2. 检查「设置→收信规则」是否误建了自动转存/归档规则；
                3. 全公司性收不到外部邮件属重大故障，立即联系邮件系统组。
                三、客户端配置参数：接收 IMAP mail.corp 993 SSL；发送 SMTP smtp.corp 465 SSL；Outlook 提示反复弹密码框时，先在网页版确认账号未锁定，再删除本地凭据（控制面板→凭据管理器）后重输。
                四、邮件误删除：30 天内可在「已删除」文件夹或网页版「恢复已删除邮件」自助找回，超 30 天需邮件系统组从备份恢复，仅保留 90 天。""");

        knowledgeBase.ingest("办公电脑性能优化与蓝屏处理手册", """
                适用范围：办公电脑卡顿、蓝屏、黑屏、外设异常。
                一、电脑卡顿：
                1. 任务管理器查看 CPU/内存占用 Top 进程，常见为安全扫描、索引服务、浏览器多开，关闭后观察；
                2. C 盘剩余空间低于 10% 会显著变慢：运行「磁盘清理」，迁移大文件到 D 盘或企业网盘；
                3. 开机自启项过多：任务管理器→启动，禁用非必要项；
                4. 以上无效且使用超过 4 年，走 OA「设备更换评估」流程申请换机。
                二、蓝屏处理：
                1. 拍下蓝屏页面（重点记录错误代码，如 MEMORY_MANAGEMENT）；
                2. 记录近期变更：是否新装软件/更新驱动/接新外设，能进安全模式则卸载对应项；
                3. 频繁蓝屏多为内存条接触不良或硬盘坏道：报修时附蓝屏照片与设备资产号，桌面运维组上门检测，必要时更换硬件；
                4. 涉及系统重装的，务必先确认用户数据已备份到企业网盘，并让用户签字确认。
                三、显示器黑屏：重新插拔视频线、更换线材交叉验证；笔记本合盖唤醒失败长按电源 10 秒强制重启再验证。""");

        knowledgeBase.ingest("业务系统页面报错处理流程（ERP/OA/CRM）", """
                适用范围：ERP、OA、CRM 等业务系统页面打不开、报错、数据异常。
                一、页面打不开/加载空白：
                1. 先确认影响范围：让同部门同事打开同一页面，多人异常属系统故障，立即联系应用支持组并按重大事件响应，不要继续自行排查；
                2. 单人异常：Ctrl+F5 强制刷新；更换浏览器（推荐 Edge/Chrome 最新版）验证；清除该站点缓存与 Cookie；
                3. 系统 A 正常、系统 B 异常：多为单系统权限问题，走权限工单。
                二、页面报错（500/403/超时）：
                1. 截图完整报错页面（含 URL 与时间点），应用支持组按时间点查服务端日志；
                2. 403 优先核查权限与账号有效期；500 需后端排查；超时多为网络链路问题，可让用户 ping/tracert 系统域名附在工单里；
                3. 下单、审批等写操作失败时，务必提醒用户不要连续重试点击，避免重复提交数据。
                三、数据不一致（金额/库存/审批状态）：属数据级问题，工单必须包含单据编号、操作时间、截图，由应用支持组转研发二线核查，禁止运维直接改库。
                四、升级标准：影响面≥1 个部门、或持续超过 30 分钟、或涉及资金/订单数据的，一律升级 P2 及以上。""");
    }

    private void seedHistoricalTickets() {
        historical("无法登录 OA 系统，提示密码错误",
                "早上开始 OA 登录一直提示密码错误，昨天还能用，试了好几次现在账号都登不上了。",
                "张伟", "zhangwei@corp.com", "账号与权限", Priority.HIGH,
                "已通过自助服务门户验证短信后重置密码，并解锁了被锁定的账号，用户电话确认可正常登录。",
                "账号连续输错被锁定，自助门户重置密码+解锁即可恢复。");

        historical("VPN 连上后打不开内网系统",
                "在家办公，VPN 显示已连接，但是 OA 和 ERP 都打不开，重启电脑也没用。",
                "李娜", "lina@corp.com", "网络与接入", Priority.HIGH,
                "指导用户在客户端诊断菜单执行重置虚拟网卡并重连，获取到 10.x 段地址后恢复访问。",
                "客户端虚拟网卡异常，重置虚拟网卡+重连即可恢复内网访问。");

        historical("打印机打印出来全是乱码",
                "三楼东打印机打文件全是乱码符号，别人打也这样，复印功能正常。",
                "王强", "wangqiang@corp.com", "办公设备", Priority.MEDIUM,
                "清空打印队列、删除重加打印机并更新至最新驱动，打印测试页正常。",
                "清空打印队列并重装驱动后恢复正常。");

        historical("邮箱满了收不到邮件",
                "客户发邮件给我提示被退回，系统提示邮箱容量已满。",
                "赵敏", "zhaomin@corp.com", "企业邮箱", Priority.MEDIUM,
                "指导用户清理大附件邮件并清空垃圾箱，同时审批通过扩容至 50G，收发恢复。",
                "清理大附件邮件并扩容至 50G 后收发恢复。");

        historical("电脑频繁蓝屏无法办公",
                "笔记本一天蓝屏两三次，蓝屏代码没记下来，最近没装过新软件。",
                "陈晨", "chenchen@corp.com", "终端设备", Priority.MEDIUM,
                "上门检测为内存条接触不良，重新插拔并烤机 2 小时稳定，已交付用户。",
                "内存条接触不良，重新插拔后稳定运行。");

        historical("ERP 报表页面报 500 错误",
                "财务报表页面点导出就报 500，换浏览器也不行，同事那里也报错。",
                "刘洋", "liuyang@corp.com", "业务系统", Priority.HIGH,
                "多人异常确认为系统故障，服务端日志定位为报表服务内存溢出，研发重启服务并扩容后恢复。",
                "报表服务内存溢出，研发侧重启服务并扩容解决。");
    }

    private void historical(String title, String description, String requester, String email,
                            String category, Priority priority, String resolution, String replyDraft) {
        Ticket t = new Ticket();
        t.setTitle(title);
        t.setDescription(description);
        t.setRequesterName(requester);
        t.setRequesterEmail(email);
        t.setCategory(category);
        t.setPriority(priority);
        t.setStatus(TicketStatus.RESOLVED);
        t.setTriageConfidence(0.9);
        t.setTriageSummary("历史工单（种子数据）");
        t.setReplyDraft(replyDraft);
        t.setResolution(replyDraft);
        t.setResolvedAt(java.time.LocalDateTime.now().minusDays(3 + (long) (Math.random() * 30)));
        ticketRepository.save(t);
    }
}
