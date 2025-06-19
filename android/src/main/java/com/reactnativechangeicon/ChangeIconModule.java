package com.reactnativechangeicon;

import androidx.annotation.NonNull;
import android.util.Log;
import android.content.Intent;
import android.app.Activity;
import android.app.Application;
import android.content.pm.PackageManager;
import android.content.ComponentName;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.module.annotations.ReactModule;

import java.util.HashSet;
import java.util.Set;

@ReactModule(name = "ChangeIcon")
public class ChangeIconModule extends ReactContextBaseJavaModule implements Application.ActivityLifecycleCallbacks {
    public static final String NAME = "ChangeIcon";
    private final ReactApplicationContext reactContext;
    private final Set<String> classesToKill = new HashSet<>();
    private Boolean iconChanged = false;
    private String componentClass = "";

    public ChangeIconModule(ReactApplicationContext reactContext, String packageName) {
        super(reactContext);
        this.reactContext = reactContext;
    }

    @Override
    @NonNull
    public String getName() {
        return NAME;
    }

    // Método auxiliar para obter o nome base do pacote (sem .debug)
    private String getBasePackageName() {
        String packageName = reactContext.getPackageName();
        if (packageName.endsWith(".debug")) {
            return packageName.substring(0, packageName.length() - 6); // Remove .debug
        }
        return packageName;
    }

    @ReactMethod
    public void getIcon(Promise promise) {
        final Activity activity = getCurrentActivity();
        if (activity == null) {
            promise.reject("ANDROID:ACTIVITY_NOT_FOUND");
            return;
        }

        final String activityName = activity.getComponentName().getClassName();
        String basePackage = getBasePackageName();

        if (activityName.endsWith("MainActivity")) {
            promise.resolve("Default");
            return;
        }
        
        String[] activityNameSplit = activityName.split("MainActivity");
        if (activityNameSplit.length != 2) {
            promise.reject("ANDROID:UNEXPECTED_COMPONENT_CLASS:" + this.componentClass);
            return;
        }
        promise.resolve(activityNameSplit[1]);
    }

    @ReactMethod
    public void changeIcon(String iconName, Promise promise) {
        final Activity activity = getCurrentActivity();
        if (activity == null) {
            promise.reject("ANDROID:ACTIVITY_NOT_FOUND");
            return;
        }

        final String newIconName = (iconName == null || iconName.isEmpty()) ? "Default" : iconName;
        String basePackage = getBasePackageName();
        final String activeClass = basePackage + ".MainActivity" + newIconName;

        Log.d("ICON_CHANGE", "Iniciando mudança de ícone para: " + newIconName);
        Log.d("ICON_CHANGE", "Classe alvo: " + activeClass);
        Log.d("ICON_CHANGE", "Package name: " + reactContext.getPackageName());
        Log.d("ICON_CHANGE", "Base package: " + basePackage);

        try {
            PackageManager pm = activity.getPackageManager();
            
            // 1. Desativa todos os aliases existentes
            String[] aliases = {"Default", "Mucura"}; // Adicione todos os seus aliases aqui
            for (String alias : aliases) {
                String className = basePackage + ".MainActivity" + alias;
                if (!className.equals(activeClass)) {
                    Log.d("ICON_CHANGE", "Desativando alias: " + className);
                    pm.setComponentEnabledSetting(
                        new ComponentName(basePackage, className),
                        PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                        PackageManager.DONT_KILL_APP
                    );
                }
            }

            // 2. Ativa o novo alias
            Log.d("ICON_CHANGE", "Ativando alias: " + activeClass);
            pm.setComponentEnabledSetting(
                new ComponentName(basePackage, activeClass),
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
            );

            // 3. Atualiza o estado interno
            this.componentClass = activeClass;

            // 4. Delay antes de atualizar o launcher (300ms)
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                try {
                    Log.d("ICON_CHANGE", "Forçando atualização do launcher após delay");
                    Intent intent = new Intent(Intent.ACTION_MAIN);
                    intent.addCategory(Intent.CATEGORY_HOME);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    activity.startActivity(intent);
                    
                    Log.d("ICON_CHANGE", "Mudança de ícone concluída com sucesso");
                    promise.resolve(newIconName);
                } catch (Exception e) {
                    Log.e("ICON_CHANGE", "Erro ao atualizar launcher", e);
                    promise.reject("ANDROID:LAUNCHER_UPDATE_FAILED", e.getMessage());
                }
            }, 300);

        } catch (Exception e) {
            Log.e("ICON_CHANGE", "Erro ao mudar ícone", e);
            promise.reject("ANDROID:ICON_CHANGE_FAILED", e.getMessage());
        }
    }

    private void completeIconChange() {
        if (!iconChanged)
            return;
        final Activity activity = getCurrentActivity();
        if (activity == null)
            return;
        
        String basePackage = getBasePackageName();
        classesToKill.forEach((cls) -> activity.getPackageManager().setComponentEnabledSetting(
                new ComponentName(basePackage, cls),
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP));
        classesToKill.remove(componentClass);
        classesToKill.clear();
        iconChanged = false;
    }

    @Override
    public void onActivityPaused(Activity activity) {
        completeIconChange();
    }

    @Override
    public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
    }

    @Override
    public void onActivityStarted(Activity activity) {
    }

    @Override
    public void onActivityResumed(Activity activity) {
    }

    @Override
    public void onActivityStopped(Activity activity) {
    }

    @Override
    public void onActivitySaveInstanceState(Activity activity, Bundle outState) {
    }

    @Override
    public void onActivityDestroyed(Activity activity) {
    }
}
