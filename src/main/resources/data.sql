INSERT INTO ai_scenarios (scenario_key, scenario_name, usage_count, description)
VALUES ('pingcha_tong', '隆中智检·评查通', 0,'通过检察业务系统质量评查案件自动分析常见问题'),
       ('liuchengyan','隆中智检·流程眼',0,'通过检察业务系统监控流程案件自动分析常见问题'),
       ('shujiantong','隆中智检·数检通',0,'通过数检通详情问题列表自动分析常见问题'),
       ('shuceyun','隆中智检·数策云',0,'通过提问可以直接查询数据指标结果'),
       ('anlituijian','案例推荐',0,'通过检察业务系统质量评查案件自动分析常见问题'),
       ('zhishiku', '法律知识库检索', 0,'通过检察业务系统质量评查案件自动分析常见问题');

UPDATE ai_scenarios SET icon = '@/assets/Group 427319457.png' WHERE scenario_key = 'pingcha_tong';
UPDATE ai_scenarios SET icon = '@/assets/Group 427319457-1.png' WHERE scenario_key = 'liuchengyan';
UPDATE ai_scenarios SET icon = '@/assets/Group 427319457-2.png' WHERE scenario_key = 'shujiantong';
UPDATE ai_scenarios SET icon = '@/assets/Group 427319457-3.png' WHERE scenario_key = 'shuceyun';
UPDATE ai_scenarios SET icon = '@/assets/Group 427319457-4.png' WHERE scenario_key = 'anlituijian';
UPDATE ai_scenarios SET icon = '@/assets/Group 427319457-5.png' WHERE scenario_key = 'zhishiku';
