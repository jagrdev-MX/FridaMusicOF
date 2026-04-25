// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    kotlin("android") version "2.1.0" apply false
    kotlin("kapt") version "2.1.0" apply false
    id("com.google.dagger.hilt.android") version "2.50" apply false
}