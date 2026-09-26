package io.github.yoima.clearthirdpersonaim.core;

/**
 * バニラの CameraType を MC 非依存にしたもの。
 * THIRD_PERSON_FRONT はカメラがプレイヤーの正面からこちらを向く視点で、エイムの邪魔にならないため既定では無効。
 */
public enum Perspective {
    FIRST_PERSON,
    THIRD_PERSON_BACK,
    THIRD_PERSON_FRONT
}
