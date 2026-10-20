package com.jeladastudios.lostsignal.rescue;

/**
 * What the lookout's keeper can say to a lost hiker over the radio: questions, and the ways of telling someone
 * where to go who cannot see where they are going. Each has the panel's button for it and the hiker's way of taking it.
 */
public enum Order {
    /** "Where are you? What can you see?" */
    WHERE(true),
    /** "Are you hurt? How are you holding up?" */
    HOW(true),
    /** "Do you have a compass?" */
    COMPASS(true),
    /** "Walk on a bearing of ... degrees." Only with a compass. */
    BEARING(false),
    /** "Follow the water downhill." */
    FOLLOW_WATER(false),
    /** "Find the trail and stay on it, uphill." */
    FOLLOW_TRAIL(false),
    /** "Go the way the rescue board points." */
    FOLLOW_SIGN(false),
    /** "Walk towards the light on the hill." At night, with the lookout's lamp lit. */
    TOWARD_LIGHT(false),
    /** "Stay where you are." */
    STAY(false),
    /** "Fire a flare." */
    FLARE(false),
    /** "Build up your fire, make it smoke." */
    SMOKE(false),
    /** "Wave your lamp." */
    LAMP(false);

    /** A question: answered, not obeyed. */
    public final boolean question;

    Order(boolean question) {
        this.question = question;
    }
}
