package com.namina.flashbrowser;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

final class DutyRewardCatalog {

    private static final Map<Integer, String> REWARD_SUMMARIES = buildRewardSummaries();

    private DutyRewardCatalog() {
    }

    static String getRewardSummary(int rewardId) {
        return REWARD_SUMMARIES.get(Integer.valueOf(rewardId));
    }

    private static Map<Integer, String> buildRewardSummaries() {
        HashMap<Integer, String> rewards = new HashMap<>();
        rewards.put(1, "海贼礼包A*1，挑战书*1，1级宝石箱*1，火影礼包A*1");
        rewards.put(2, "时之沙*5，海贼礼包*1，挑战书*3，2级宝石箱*1");
        rewards.put(3, "时之沙*10，火影海贼袋*1，品质刷新书*1，2级宝石箱*1");
        rewards.put(4, "时之沙*15，高级挑战书*1，副本挑战书*3，2级宝石箱*1");
        rewards.put(5, "火影礼包B*1，火影礼包C*1，2级宝石箱*1，穿透合成书*1");
        rewards.put(6, "高级挑战书*1，海贼礼包B*1，护甲合成书*1，2级宝石箱*2");
        rewards.put(7, "高级挑战书*2，闪避合成书*1，命中合成书*1，3级宝石箱");
        rewards.put(8, "高级升级书*2，高级挑战书*2，HP合成书*1，3级宝石箱*2");
        rewards.put(9, "4级宝箱*1，攻击合成书*1，王者之书*2，贤者之书*2");
        rewards.put(10, "惊喜植物包*1，禁咒之书*2，战神之书*2，魔神刷新书*1");
        rewards.put(11, "3级宝箱*1，增强卷轴*5，全属性传承书*1，耀世盛典*1");
        rewards.put(12, "成长刷新书*1，初级升级书*1，暗弹*1");
        rewards.put(13, "成长刷新书*2，火弹*1，雷弹*1，初级升级书*1");
        rewards.put(14, "品质刷新书*1，旋风*1，高级金币*1，初级升级书*2");
        rewards.put(15, "品质刷新书*2，高级金币*1，激光*1，黑暗*1");
        rewards.put(16, "品质刷新书*2，财神宝箱*1，中级升级书*1，攻击合成书*1");
        rewards.put(17, "品质刷新书*3，财神宝箱*2，中级升级书*2，HP合成书*1");
        rewards.put(18, "品质刷新书*3，财神宝箱*2，中级升级书*3，神秘礼包*1");
        rewards.put(19, "品质刷新书*4，财神宝箱*3，二星升级书*2，攻击合成书*1");
        rewards.put(20, "品质刷新书*5，财神宝箱*3，HP合成书*1，魔神刷新书*5");
        rewards.put(21, "极化石*1，强力宝箱*1，时之沙*10，挑战书*2");
        rewards.put(22, "强力宝箱*1，时之沙*20，启蒙之书*3，高级挑战书*1");
        rewards.put(23, "品质刷新书*5，时之沙*30，高级挑战书*2，5级宝石箱*1");
        rewards.put(24, "时之沙*10，挑战书*2，极化石*1，海贼礼包A*1");
        rewards.put(25, "时之沙*15，高级挑战书*1，启蒙之书*1，海贼礼包B*1");
        rewards.put(26, "时之沙*20，高级挑战书*2，进阶之书*1，海贼礼包B*2");
        rewards.put(27, "时之沙*30，高级挑战书*10，5星攻击书*1，5级宝石箱*1");
        rewards.put(28, "礼券*20，高级金币*1，1星攻击书*1，1级宝石箱*1");
        rewards.put(29, "礼券*50，普通钻石*1，2星攻击书*1，2级宝石箱*1");
        rewards.put(30, "中级礼券*2，高级钻石*1，2级宝石箱*1，3星攻击书*1");
        rewards.put(31, "高级礼券*3，黑暗*1，3级宝石箱*1，3星攻击书*1");
        rewards.put(32, "高级礼券*2，分裂书残页*3，4星攻击书*1，3级宝石箱*1");
        rewards.put(33, "高级礼券*3，4级宝石箱*1，4星攻击书*1，闪避合成书*1");
        rewards.put(35, "品质刷新书*1，挑战书*1，副本挑战书*1，初级升级书*1");
        rewards.put(36, "品质刷新书*1，挑战书*1，副本挑战书*2，中级升级书*1");
        rewards.put(37, "品质刷新书*2，挑战书*2，副本挑战书*3，高级升级书*1");
        rewards.put(38, "品质刷新书*2，挑战书*3，副本挑战书*1，启蒙之书*1");
        rewards.put(39, "品质刷新书*3，挑战书*3，副本挑战书*1，启蒙之书*1");
        rewards.put(40, "品质刷新书*3，命中合成书*1，增强卷轴*1，启蒙之书*2");
        rewards.put(41, "品质刷新书*4，闪避合成书*1，增强卷轴*3，启蒙之书*3");
        rewards.put(42, "品质刷新书*5，传承卷轴*5，攻击传承书*1，耀世盛典*1");
        rewards.put(43, "品质刷新书*4，1星攻击书*4，高级金币*4，初级升级书*4");
        rewards.put(45, "品质刷新书*3，1星闪避书*3，高级金币*3，启蒙之书*3");
        rewards.put(46, "海贼礼包*1，4星闪避书*3，财神宝箱*3，王者之书*3");
        rewards.put(47, "品质刷新书*4，1星命中书*4，高级金币*4，初级升级书*4");
        rewards.put(48, "死神宝箱*4，4星命中书*4，死神素材箱*4，财神宝箱*4");
        rewards.put(49, "品质刷新书*3，1星HP书*3，高级金币*3，启蒙之书*3");
        rewards.put(51, "品质刷新书*3，1星护甲书*3，高级金币*3，初级升级书*3");
        rewards.put(53, "品质刷新书*3，1星穿透书*3，高级金币*3，启蒙之书*2");
        rewards.put(55, "品质刷新书*10，高级升级书*5，5星攻击书*2，启蒙之书*2");
        rewards.put(57, "品质刷新书*5，高级升级书*3，4星闪避书*2，启蒙之书*2");
        rewards.put(59, "品质刷新书*5，高级升级书*3，4星HP书*2，启蒙之书*2");
        rewards.put(61, "太阳花雕像*1");
        rewards.put(62, "礼券*100，品质刷新书*1，1级宝石箱*1");
        rewards.put(63, "礼券*300，品质刷新书*3，2级宝石箱*1");
        rewards.put(64, "礼券*500，品质刷新书*5，4级宝石箱*1");
        rewards.put(65, "品质刷新书*1，高级挑战书*1，初级升级书*1，2级宝石箱*1");
        rewards.put(66, "品质刷新书*2，高级挑战书*2，中级升级书*1，3级宝石箱*1");
        rewards.put(67, "品质刷新书*3，高级挑战书*3，高级升级书*1，4级宝石箱*1");
        rewards.put(68, "品质刷新书*5，增强卷轴*1，5级宝石箱*1，HP合成书*3");
        rewards.put(69, "魔神刷新书*10，全属性传承书*1，传承卷轴*3，6级宝石箱*1");
        rewards.put(70, "礼券*100，成长刷新书*1，品质刷新书*1，高级金币*3");
        rewards.put(71, "礼券*150，初级升级书*1，风弹*1，海贼礼包*1");
        rewards.put(72, "礼券*200，高级升级书*1，光之宝箱*1，2级宝石箱*3");
        rewards.put(73, "礼券*250，增强卷轴*10，风之宝箱*1，HP合成书*1");
        rewards.put(74, "礼券*300，强攻*1，水之宝箱*1，启蒙之书*1");
        rewards.put(75, "礼券*400，初级专属技能升级书*1，雷之宝箱*1，4星攻击书*1");
        rewards.put(76, "礼券*500，中级专属技能升级书*1，4星HP书*1，4级宝石箱*2");
        rewards.put(77, "礼券*800，品质刷新书*20，魔神刷新书*10，魔神植物箱");
        return Collections.unmodifiableMap(rewards);
    }
}
