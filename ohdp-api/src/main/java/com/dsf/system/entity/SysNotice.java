package com.dsf.system.entity;

import com.dsf.common.persistence.BaseAuditEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "sys_notice")
public class SysNotice extends BaseAuditEntity {

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    @Column(name = "notice_type", length = 32)
    private String noticeType;

    @Column(name = "publisher", length = 64)
    private String publisher;

    @Column(name = "publish_time")
    private LocalDateTime publishTime;

    @Column(name = "enabled")
    private Boolean enabled = true;
}
