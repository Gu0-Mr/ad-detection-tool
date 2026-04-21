# GitHub 部署指南

## 本地提交已完成

代码已成功提交到本地Git仓库，共修改/新增以下文件：

### 修改的文件
1. `AdDetectorSimple.kt` - 核心检测逻辑（重写）
2. `AdDetectionService.kt` - 服务调用适配（重写）
3. `Constants.kt` - 新增常量

### 新增的文件
4. `检测逻辑测试指南.md` - 测试文档

## 推送到GitHub步骤

### 步骤1：创建GitHub仓库（如果没有）

1. 访问 https://github.com/new
2. 填写仓库名称：`ad-detector` 或自定义名称
3. 选择 Private 或 Public
4. 点击 "Create repository"

### 步骤2：添加远程仓库并推送

```bash
cd 广告检测工具_stable

# 添加远程仓库（替换 YOUR_USERNAME 为你的GitHub用户名）
git remote add origin https://github.com/YOUR_USERNAME/ad-detector.git

# 推送代码（首次推送需设置上游分支）
git push -u origin master
```

### 步骤3：验证CI自动构建

推送后，GitHub Actions会自动触发构建：

1. 访问 `https://github.com/YOUR_USERNAME/ad-detector/actions`
2. 查看 "Android CI" workflow 状态
3. 构建成功后，在 Artifacts 下载 APK

## 构建产物

CI完成后的产物：
- **Debug APK**: `app/build/outputs/apk/debug/app-debug.apk`
- 下载链接会显示在 Actions 运行页面

## 手动触发构建

如果需要手动触发构建：

1. 访问仓库的 Actions 页面
2. 选择 "Android CI" workflow
3. 点击 "Run workflow"
4. 选择分支并运行

## 后续开发

后续代码更新只需：

```bash
git add .
git commit -m "描述你的修改"
git push
```

GitHub Actions 会自动检测并重新构建。
