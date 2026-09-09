# CI workflow

`github-workflow-build.yml` is the project's GitHub Actions pipeline (JDK 17 temurin,
Gradle cache, unit tests, `assembleDebug`, `assembleRelease`, lint, APK + report artifacts).

It is kept here because the automation account that pushed this branch does not hold the
GitHub App `workflows` permission and therefore cannot create files under `.github/workflows/`.

To activate it:

```bash
mkdir -p .github/workflows
cp ci/github-workflow-build.yml .github/workflows/build.yml
git add .github/workflows/build.yml && git commit -m "Add CI" && git push
```
