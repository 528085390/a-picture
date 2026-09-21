create database if not exists a_picture;
use a_picture;


-- 用户表
create table if not exists user
(
    id           bigint auto_increment comment 'id' primary key,
    userAccount  varchar(256)                           not null comment '账号',
    userPassword varchar(512)                           not null comment '密码',
    userName     varchar(256)                           null comment '用户昵称',
    userAvatar   varchar(1024)                          null comment '用户头像',
    userProfile  varchar(512)                           null comment '用户简介',
    userRole     varchar(256) default 'user'            not null comment '用户角色：user/admin',
    editTime     datetime     default CURRENT_TIMESTAMP not null comment '编辑时间',
    createTime   datetime     default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime   datetime     default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    isDelete     tinyint      default 0                 not null comment '是否删除',
    UNIQUE KEY uk_userAccount (userAccount),
    INDEX idx_userName (userName)
) comment '用户' collate = utf8mb4_unicode_ci;

-- 图片表
create table if not exists picture
(
    id           bigint auto_increment comment 'id' primary key,
    type         varchar(32)                        not null comment '图片类型: live|static',
    url          varchar(512)                       not null comment '图片 url',
    previewUrl   varchar(512)                       null comment '预览图 url',
    name         varchar(128)                       not null comment '图片名称',
    introduction varchar(512)                       null comment '简介',
    category     varchar(64)                        null comment '分类',
    tags         varchar(512)                       null comment '标签（JSON 数组）',
    picSize      bigint                             null comment '图片体积',
    picWidth     int                                null comment '图片宽度',
    picHeight    int                                null comment '图片高度',
    picScale     double                             null comment '图片宽高比例',
    picFormat    varchar(32)                        null comment '图片格式',
    userId       bigint                             not null comment '创建用户 id',
    spaceId      bigint                             null comment '所属空间 id（NULL 表示公共图库）',
    createTime   datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    editTime     datetime default CURRENT_TIMESTAMP not null comment '编辑时间',
    updateTime   datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    isDelete     tinyint  default 0                 not null comment '是否删除',
    INDEX idx_name (name),                 -- 提升基于图片名称的查询性能
    INDEX idx_introduction (introduction), -- 用于模糊搜索图片简介
    INDEX idx_category (category),         -- 提升基于分类的查询性能
    INDEX idx_tags (tags),                 -- 提升基于标签的查询性能
    INDEX idx_userId (userId),             -- 提升基于用户 ID 的查询性能
    INDEX idx_spaceId (spaceId)            -- 提升基于空间 ID 的查询性能
) comment '图片' collate = utf8mb4_unicode_ci;

-- live 图视频表
create table if not exists live_video
(
    id    bigint auto_increment comment 'id' primary key,
    name  varchar(128) not null comment '视频名称',
    url   varchar(512) not null comment '视频 url',
    picId bigint comment '关联图片 id',
    INDEX idx_name (name),  -- 提升基于视频名称的查询性能
    INDEX idx_picId (picId) -- 提升基于图片 ID 的查询性能
) comment 'live 图视频' collate = utf8mb4_unicode_ci;

-- 空间表
create table if not exists space
(
    id         bigint auto_increment comment 'id' primary key,
    spaceName  varchar(128)                       null comment '空间名称',
    spaceLevel int      default 0                 not null comment '空间级别：0-普通版 1-专业版 2-旗舰版',
    maxSize    bigint   default 0                 not null comment '空间图片的最大总大小',
    maxCount   bigint   default 0                 not null comment '空间图片的最大数量',
    totalSize  bigint   default 0                 not null comment '当前空间下图片的总大小',
    totalCount bigint   default 0                 not null comment '当前空间下的图片数量',
    userId     bigint                             not null comment '创建用户 id',
    spaceType  int      default 0                 not null comment '空间类型：0-私有空间 1-团队空间',
    status     int      default 0                 not null comment '空间状态：0-正常 1-禁用',
    createTime datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    editTime   datetime default CURRENT_TIMESTAMP not null comment '编辑时间',
    updateTime datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    isDelete   tinyint  default 0                 not null comment '是否删除',
    INDEX idx_userId (userId),            -- 提升基于用户的查询效率
    INDEX idx_spaceName (spaceName),      -- 提升基于空间名称的查询效率
    INDEX idx_spaceLevel (spaceLevel),    -- 提升按空间级别查询的效率
    INDEX idx_spaceType (spaceType)       -- 提升按空间类型查询的效率
) comment '空间' collate = utf8mb4_unicode_ci;
