package com.nomnomsom.starwarsshop.data.model

enum class DownloadState(val value: String) {
    NONE("none"),
    DOWNLOADING("downloading"),
    DOWNLOADED("downloaded"),
    ERROR("error");

    companion object {
        fun fromValue(value: String): DownloadState =
            entries.find { it.value == value } ?: NONE
    }
}
