# ProGuard rules for YunoTools
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Application
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable
-keep public class * extends android.view.View {
    public <init>(android.content.Context);
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
}
-keep class com.yuno.tools.data.** { *; }
-keep class com.yuno.tools.util.** { *; }