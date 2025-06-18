package com.reactnativechangeicon;

import androidx.annotation.NonNull;
import android.util.Log;
import android.app.Activity;
import android.app.Application;
import android.content.pm.PackageManager;
import android.content.ComponentName;
import android.os.Bundle;

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
    private final String packageName;
    private final Set<String> classesToKill = new HashSet<>();
    private Boolean iconChanged = false;
    private String componentClass = "";

    public ChangeIconModule(ReactApplicationContext reactContext, String packageName) {
        super(reactContext);
        this.packageName = packageName;
    }

    @Override
    @NonNull
    public String getName() {
        return NAME;
    }

    @ReactMethod
    public void getIcon(Promise promise) {
        final Activity activity = getCurrentActivity();
        if (activity == null) {
            promise.reject("ANDROID:ACTIVITY_NOT_FOUND");
            return;
        }

        final String activityName = activity.getComponentName().getClassName();

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
        return;
    }

 @ReactMethod
public void changeIcon(String iconName, Promise promise) {
    final Activity activity = getCurrentActivity();
    if (activity == null) {
        promise.reject("ANDROID:ACTIVITY_NOT_FOUND");
        return;
    }

    final String newIconName = (iconName == null || iconName.isEmpty()) ? "Default" : iconName;
    final String activeClass = "br.com.meliuz" + ".MainActivity" + newIconName;

    Log.d("ICON_CHANGE", "Iniciando mudança de ícone");
    Log.d("ICON_CHANGE", "Novo ícone solicitado: " + newIconName);
    Log.d("ICON_CHANGE", "Classe ativa atual: " + this.componentClass);
    Log.d("ICON_CHANGE", "Nova classe a ser ativada: " + activeClass);

    if (this.componentClass.equals(activeClass)) {
        Log.w("ICON_CHANGE", "Ícone já está em uso: " + this.componentClass);
        promise.reject("ANDROID:ICON_ALREADY_USED:" + this.componentClass);
        return;
    }

    try {
        // 1. Desabilita o ícone atual (se existir)
        if (!this.componentClass.isEmpty()) {
            Log.d("ICON_CHANGE", "Desativando classe atual: " + this.componentClass);
            activity.getPackageManager().setComponentEnabledSetting(
                new ComponentName(this.packageName, this.componentClass),
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
            );
            Log.d("ICON_CHANGE", "Classe desativada com sucesso: " + this.componentClass);
        } else {
            Log.d("ICON_CHANGE", "Nenhuma classe ativa para desativar (primeira execução)");
        }

        // 2. Habilita o novo ícone
        Log.d("ICON_CHANGE", "Ativando nova classe: " + activeClass);
        activity.getPackageManager().setComponentEnabledSetting(
            new ComponentName(this.packageName, activeClass),
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP
        );
        Log.d("ICON_CHANGE", "Nova classe ativada com sucesso: " + activeClass);

        // 3. Força atualização do launcher
        Log.d("ICON_CHANGE", "Forçando atualização do launcher");
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_HOME);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        activity.startActivity(intent);
        Log.d("ICON_CHANGE", "Launcher atualizado");

        // 4. Atualiza estado interno
        this.componentClass = activeClass;
        iconChanged = true;

        Log.d("ICON_CHANGE", "Mudança de ícone concluída com sucesso");
        promise.resolve(newIconName);
    } catch (Exception e) {
        Log.e("ICON_CHANGE", "Erro ao mudar ícone", e);
        promise.reject("ANDROID:ICON_INVALID", e.getMessage());
    }
}

    private void completeIconChange() {
        if (!iconChanged)
            return;
        final Activity activity = getCurrentActivity();
        if (activity == null)
            return;
        
        classesToKill.forEach((cls) -> activity.getPackageManager().setComponentEnabledSetting(
                new ComponentName(this.packageName, cls),
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
