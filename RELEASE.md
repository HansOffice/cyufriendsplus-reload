# Release Guide

这份文件给维护者看，用来记录 CyuFriends Reload 的发布、打包和回滚流程

## 发布前检查

```bash
mvn -DskipTests -Ppaper package
mvn -DskipTests -Ppaper,full package
mvn -DskipTests -Pfolia package
```

确认这些文件存在：

- `target/cyufriends-reload-paper-1.1.3.jar`
- `target/cyufriends-reload-paper-1.1.3-legacy-all.jar`
- `target/cyufriends-reload-paper-1.1.3-api.jar`
- `target/cyufriends-reload-folia-1.1.3.jar`

确认 `src/main/resources/plugin.yml` 里的版本来自 `${project.version}`，实际版本以 `pom.xml` 为准

## 版本号

发布新版本时只改 `pom.xml` 的 `<version>`：

```xml
<version>1.1.3</version>
```

然后同步更新：

- `README.md` 中的示例 jar 名称
- `CHANGELOG.md` 新增版本记录

## 打 tag

```bash
git tag v1.1.3
git push origin v1.1.3
```

GitHub Actions 会在 tag 推送后重新构建 jar，并上传 Actions artifact：

- Paper / Folia 构建 artifact
- API jar artifact
- Maven package 到 GitHub Packages

GitHub Actions 会在 tag 推送后创建对应 Release，并上传 Paper、Folia、API 和 `paper-legacy-all` 四个服务端构建包。Paper 完整包使用 `-Ppaper,full` 构建，配套代理端为私有插件，不上传到本仓库或本仓库的 Release

已经公开的 tag 不移动；修复请发新的补丁版本

## 回滚

如果新版发布后发现严重问题：

1. 在 GitHub Releases 或 Actions artifacts 找到上一个可用版本
2. 服务器先换回旧 jar
3. 代码侧从对应 tag 新建修复分支

```bash
git checkout -b fix/v1.1.3-hotfix v1.1.3
```

修复完成后发布新的补丁版本，不建议强改已经公开的 tag
