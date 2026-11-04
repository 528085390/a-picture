SET NAMES utf8mb4;
USE a_picture;
INSERT INTO user (userAccount, userPassword, userName, userRole)
VALUES ('admin', 'a66abb5684c45962d887564f08346e8d', '管理员', 'admin')
ON DUPLICATE KEY UPDATE userPassword = VALUES(userPassword), userName = VALUES(userName), userRole = 'admin';
