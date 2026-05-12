# Release Guide

这份文件给维护者看，用来记录 CyuFriends Reload 的发布、打包和回滚流程

## 发布前检查

```bash
mvn -DskipTests clean package
```

确认这些文件存在：

- `target/cyufriends-reload-1.0.jar`
- `target/cyufriends-reload-1.0-api.jar`

确认 `src/main/resources/plugin.yml` 里的版本来自 `${project.version}`，实际版本以 `pom.xml` 为准

## 版本号

发布新版本时只改 `pom.xml` 的 `<version>`：

```xml
<version>1.0.1</version>
```

然后同步更新：

- `README.md` 中的示例 jar 名称
- `CHANGELOG.md` 新增版本记录

## 打 tag

```bash
git tag v1.0
git push origin v1.0
```

GitHub Actions 会在 tag 推送后重新构建 jar，创建 GitHub Release，并上传：

- 插件本体 jar
- API jar
- Maven package 到 GitHub Packages

如果需要重新发布一个还没有公开过的首发 tag，可以移动 tag 后强制推送：

```bash
git tag -f v1.0
git push origin v1.0 --force
```

公开发布后不要强改已经公开的 tag，补丁请发新版本

## 回滚

如果新版发布后发现严重问题：

1. 在 GitHub Releases 或 Actions artifacts 找到上一个可用版本
2. 服务器先换回旧 jar
3. 代码侧从对应 tag 新建修复分支

```bash
git checkout -b fix/v1.0-hotfix v1.0
```

修复完成后发布新的补丁版本，不建议强改已经公开的 tag
