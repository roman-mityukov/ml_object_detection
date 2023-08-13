package ru.pluscards.ml_object_detection.camera

enum class CameraProvider {
    phone, glasses
}

interface CameraService {
    fun init()
    fun close()
}
