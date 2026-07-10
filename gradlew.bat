@rem Gradle wrapper for Windows
@echo off
set JAVA_EXE=%JAVA_HOME%\bin\java.exe
if not exist "%JAVA_EXE%" set JAVA_EXE=java
"%JAVA_EXE%" -classpath "gradle\wrapper\gradle-wrapper.jar" -Dgradle.user.home="%USERPROFILE%\.gradle" org.gradle.wrapper.GradleWrapperMain %*
