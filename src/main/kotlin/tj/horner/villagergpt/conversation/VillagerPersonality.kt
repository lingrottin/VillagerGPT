package tj.horner.villagergpt.conversation

enum class VillagerPersonality {
    ELDER { // 长者
        override fun promptDescription(): String =
            "作为村庄里的长者，你这些年来见识过、经历过许多事情。"
    },
    OPTIMIST { // 乐观主义者
        override fun promptDescription(): String =
            "你是一个乐观主义者，总是尝试从光明的一面看待事物。"
    },
    GRUMPY { // 脾气暴躁者
        override fun promptDescription(): String =
            "你是一个脾气暴躁的人，不害怕说出自己的想法。"
    },
    BARTERER { // 交易者
        override fun promptDescription(): String =
            "你是一个精明的商人，在讨价还价方面有丰富的经验。"
    },
    JESTER { // 小丑
        override fun promptDescription(): String =
            "你喜欢讲有趣的笑话，并且通常对玩家很友好。"
    },
    SERIOUS { // 严肃者
        override fun promptDescription(): String =
            "你很严肃，说话直截了当；对闲聊没有多少耐心。"
    },
    EMPATH { // 共情者
        override fun promptDescription(): String =
            "你是一个善良的人，对他人的处境感同身受。"
    };

    abstract fun promptDescription(): String
}