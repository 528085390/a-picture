SET NAMES utf8mb4;
USE a_picture;
-- 管理员种子账号。后端口令方案是 md5(SALT + 明文)，SALT 固定为 "apicture"
-- （见 UserServiceImpl#getEncryptPassword），所以哈希必须按下面这条命令生成，
-- 直接对明文做 md5 是登录不上的：
--   printf 'apicture%s' '你的新密码' | md5sum
-- 下面这条是示例口令 "admin123456" 的哈希，部署前请替换成自己的。
-- 注意：MySQL 只在数据卷为空时执行本目录，已初始化过的库改这里不会生效。
INSERT INTO user (userAccount, userPassword, userName, userRole)
VALUES ('admin', '5862c823442f4b0f825de52aa08d9e71', '管理员', 'admin')
ON DUPLICATE KEY UPDATE userPassword = VALUES(userPassword), userName = VALUES(userName), userRole = 'admin';
