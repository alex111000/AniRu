# Replace the repository with AniRu v1.2.4 clean source

The clean archive is intended to replace the current working tree, while preserving the repository's `.git` directory.

After uploading `AniRu-v1.2.4-clean-full-source.zip` to `/workspaces/AniRu`, remove every top-level item except `.git` and the uploaded archive, extract the archive into the repository root, verify `git status`, then commit and push.

Because the Codespaces integration token may not trigger or dispatch GitHub Actions, the build can be started from the repository's **Actions → Build AniRu TV APK → Run workflow → main** page if no push-triggered run appears.

Expected artifact: `AniRu-TV-v1.2.4-debug`.
