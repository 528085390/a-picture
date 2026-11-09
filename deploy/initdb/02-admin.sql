SET NAMES utf8mb4;
USE a_picture;
-- 管理员种子账号。下面的 MD5 是示例口令 "admin123456" 的哈希，
-- 部署前请替换成自己的：printf '%s' '你的新密码' | md5sum
-- 注意：MySQL 只在数据卷为空时执行本目录，已初始化过的库改这里不会生效。
INSERT INTO user (userAccount, userPassword, userName, userRole)
VALUES ('admin', 'a66abb5684c45962d887564f08346e8d', '管理员', 'admin')
ON DUPLICATE KEY UPDATE userPassword = VALUES(userPassword), userName = VALUES(userName), userRole = 'admin';
