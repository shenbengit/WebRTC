# WebRTC
Android端WebRTC源码(m105版本)
## 引入
### 将JitPack存储库添加到您的项目中(项目根目录下build.gradle文件)
```gradle
allprojects {
    repositories {
        ...
        maven { url 'https://jitpack.io' }
    }
}
```
### 添加依赖
[![](https://jitpack.io/v/shenbengit/WebRTC.svg)](https://jitpack.io/#shenbengit/WebRTC)
> 在您引入项目的build.gradle中添加
```gradle
dependencies {
    implementation 'com.github.shenbengit:WebRTC:Tag'
}
```
