package com.galaxyairpods.health

import androidx.health.connect.client.records.ExerciseSessionRecord

enum class WorkoutType(
    val key: String,
    val koreanLabel: String,
    val englishLabel: String,
    val healthConnectType: Int,
    val recordsGps: Boolean = false,
) {
    WALKING("WALKING", "걷기", "Walking", ExerciseSessionRecord.EXERCISE_TYPE_WALKING, true),
    RUNNING("RUNNING", "달리기", "Running", ExerciseSessionRecord.EXERCISE_TYPE_RUNNING, true),
    CYCLING("CYCLING", "사이클", "Cycling", ExerciseSessionRecord.EXERCISE_TYPE_BIKING, true),
    HIKING("HIKING", "등산", "Hiking", ExerciseSessionRecord.EXERCISE_TYPE_HIKING, true),
    RUNNING_TREADMILL("RUNNING_TREADMILL", "트레드밀", "Treadmill", ExerciseSessionRecord.EXERCISE_TYPE_RUNNING_TREADMILL),
    STAIR_CLIMBING("STAIR_CLIMBING", "계단 오르기", "Stair climbing", ExerciseSessionRecord.EXERCISE_TYPE_STAIR_CLIMBING),
    STAIR_MACHINE("STAIR_MACHINE", "스텝밀", "Stair machine", ExerciseSessionRecord.EXERCISE_TYPE_STAIR_CLIMBING_MACHINE),
    STRENGTH("STRENGTH", "근력 운동", "Strength", ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING),
    WEIGHTLIFTING("WEIGHTLIFTING", "웨이트 트레이닝", "Weightlifting", ExerciseSessionRecord.EXERCISE_TYPE_WEIGHTLIFTING),
    ELLIPTICAL("ELLIPTICAL", "일립티컬", "Elliptical", ExerciseSessionRecord.EXERCISE_TYPE_ELLIPTICAL),
    HIIT("HIIT", "인터벌", "HIIT", ExerciseSessionRecord.EXERCISE_TYPE_HIGH_INTENSITY_INTERVAL_TRAINING),
    ROWING("ROWING", "로잉", "Rowing", ExerciseSessionRecord.EXERCISE_TYPE_ROWING),
    ROWING_MACHINE("ROWING_MACHINE", "실내 로잉", "Indoor rowing", ExerciseSessionRecord.EXERCISE_TYPE_ROWING_MACHINE),
    SWIMMING_POOL("SWIMMING_POOL", "수영", "Pool swimming", ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL),
    SWIMMING_OPEN_WATER("SWIMMING_OPEN_WATER", "야외 수영", "Open-water swimming", ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_OPEN_WATER, true),
    BOXING("BOXING", "복싱", "Boxing", ExerciseSessionRecord.EXERCISE_TYPE_BOXING),
    BASKETBALL("BASKETBALL", "농구", "Basketball", ExerciseSessionRecord.EXERCISE_TYPE_BASKETBALL),
    SOCCER("SOCCER", "축구", "Soccer", ExerciseSessionRecord.EXERCISE_TYPE_SOCCER),
    BADMINTON("BADMINTON", "배드민턴", "Badminton", ExerciseSessionRecord.EXERCISE_TYPE_BADMINTON),
    TENNIS("TENNIS", "테니스", "Tennis", ExerciseSessionRecord.EXERCISE_TYPE_TENNIS),
    TABLE_TENNIS("TABLE_TENNIS", "탁구", "Table tennis", ExerciseSessionRecord.EXERCISE_TYPE_TABLE_TENNIS),
    VOLLEYBALL("VOLLEYBALL", "배구", "Volleyball", ExerciseSessionRecord.EXERCISE_TYPE_VOLLEYBALL),
    GOLF("GOLF", "골프", "Golf", ExerciseSessionRecord.EXERCISE_TYPE_GOLF, true),
    SKIING("SKIING", "스키", "Skiing", ExerciseSessionRecord.EXERCISE_TYPE_SKIING, true),
    DANCE("DANCE", "댄스", "Dance", ExerciseSessionRecord.EXERCISE_TYPE_DANCING),
    PILATES("PILATES", "필라테스", "Pilates", ExerciseSessionRecord.EXERCISE_TYPE_PILATES),
    YOGA("YOGA", "요가", "Yoga", ExerciseSessionRecord.EXERCISE_TYPE_YOGA),
    STRETCHING("STRETCHING", "스트레칭", "Stretching", ExerciseSessionRecord.EXERCISE_TYPE_STRETCHING),
    OTHER("OTHER", "기타 운동", "Other", ExerciseSessionRecord.EXERCISE_TYPE_OTHER_WORKOUT);

    fun label(isKorean: Boolean) = if (isKorean) koreanLabel else englishLabel

    companion object {
        fun fromKey(key: String?): WorkoutType = entries.firstOrNull { it.key == key } ?: OTHER
    }
}
