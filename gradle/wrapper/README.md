# Gradle Wrapper

此文件夹需要包含 `gradle-wrapper.jar` 文件才能运行 Gradle 构建。

## 获取方法

### 方法1: 从Gradle官方下载
访问 https://services.gradle.org/distributions/gradle-8.7-bin.zip
下载并解压，找到 `gradle-8.7/lib/gradle-wrapper-8.7.jar`
复制到当前目录并重命名为 `gradle-wrapper.jar`

### 方法2: 使用已安装的Gradle生成
如果你已经安装了Gradle，在项目根目录运行：
```bash
gradle wrapper --gradle-version 8.7
```

### 方法3: 从GitHub下载
```bash
curl -L -o gradle/wrapper/gradle-wrapper.jar \
  "https://github.com/gradle/gradle/raw/v8.7.0/gradle/wrapper/gradle-wrapper.jar"
```

### 方法4: 从Maven Central下载
```bash
wget -P gradle/wrapper/ \
  https://repo1.maven.org/maven2/org/gradle/gradle-tooling-api/8.7/gradle-tooling-api-8.7.jar
```

## 验证
下载完成后，运行以下命令验证：
```bash
./gradlew --version
```
