-- 演示账号：demo / demo1234、alice / alice1234（仅本地演示）
INSERT INTO users (id, username, display_name, password_hash, created_at)
VALUES
    ('usr_01M607ZMC7QETA6BERJWY9P6NB', 'demo', 'Demo User',
     '$2b$10$iW9SRGufh0jWMZZlNEEh8ed/Fq8J1C1wRHf3VevXBLjWT429cvHwW', UTC_TIMESTAMP(3)),
    ('usr_01M607ZMC8XNKQ0P8SRGWPJMXJ', 'alice', 'Alice',
     '$2b$10$LaDmHMUtEF5zn6n3RwLswelzS/wVJn/PQbNUj31afW/iZ9UFDG7Dy', UTC_TIMESTAMP(3));

-- 3 个分类以上、6 个模板；required_image_count 覆盖 1/2/3，max_file_size_bytes 覆盖 2/5/10 MB
INSERT INTO templates (id, title, category, cover_url, description,
                       required_image_count, accepted_mime_types, max_file_size_bytes,
                       enabled, sort_order, created_at)
VALUES
    ('demo-postcard', '旅行明信片', '创意图片', '/demo/postcard-cover.png',
     '上传两张图片，模拟生成一张旅行明信片', 2, 'image/jpeg,image/png', 5242880, TRUE, 10, UTC_TIMESTAMP(3)),

    ('city-poster', '城市海报', '创意图片', '/demo/city-cover.png',
     '上传一张城市照片，生成一张复古风格海报', 1, 'image/jpeg,image/png', 2097152, TRUE, 20, UTC_TIMESTAMP(3)),

    ('pet-portrait', '萌宠写真', '宠物写真', '/demo/pet-portrait-cover.png',
     '上传一张宠物照片，生成柔和背景的写真', 1, 'image/jpeg,image/png', 5242880, TRUE, 30, UTC_TIMESTAMP(3)),

    ('pet-family', '多宠合影', '宠物写真', '/demo/pet-family-cover.png',
     '上传三张宠物照片，合成一张温馨合影', 3, 'image/jpeg,image/png', 10485760, TRUE, 40, UTC_TIMESTAMP(3)),

    ('holiday-card', '节日贺卡', '节日贺卡', '/demo/holiday-cover.png',
     '上传两张照片，生成一张节日贺卡', 2, 'image/jpeg,image/png', 2097152, TRUE, 50, UTC_TIMESTAMP(3)),

    ('wedding-collage', '婚礼拼图', '婚礼情侣', '/demo/wedding-cover.png',
     '上传三张照片，生成一张婚礼主题拼图', 3, 'image/jpeg,image/png', 10485760, TRUE, 60, UTC_TIMESTAMP(3));