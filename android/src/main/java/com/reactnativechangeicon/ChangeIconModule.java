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

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@ReactModule(name = "ChangeIcon")
public class ChangeIconModule extends ReactContextBaseJavaModule implements Application.ActivityLifecycleCallbacks {
    public static final String NAME = "ChangeIcon";
    private static final String TAG = "ICON_CHANGE";
    private final ReactApplicationContext reactContext;
    private final Set<String> classesToKill = new HashSet<>();
    private Boolean iconChanged = false;
    private String componentClass = "";

    public ChangeIconModule(ReactApplicationContext reactContext) {
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
            return packageName.substring(0, packageName.length() - 6);
        }
        return packageName;
    }

    private List<String> getValidAliases() {
        return Arrays.asList("Default", "Mucura");
    }

    @ReactMethod
    public void getIcon(Promise promise) {
        try {
            Activity activity = getCurrentActivity();
            if (activity == null) {
                Log.e(TAG, "getIcon: Activity não encontrada");
                promise.reject("ANDROID:ACTIVITY_NOT_FOUND");
                return;
            }
            
            ComponentName component = activity.getComponentName();
            String className = component.getClassName();
            Log.d(TAG, "Classe da atividade atual: " + className);
            
            for (String alias : getValidAliases()) {
                if (className.endsWith("MainActivity" + alias)) {
                    Log.d(TAG, "Ícone atual encontrado: " + alias);
                    promise.resolve(alias);
                    return;
                }
            }
            
            Log.w(TAG, "Nenhum alias correspondente encontrado. Usando fallback Default");
            promise.resolve("Default");
        } catch (Exception e) {
            Log.e(TAG, "Erro ao obter ícone: " + e.getMessage(), e);
            promise.reject("ANDROID:ICON_ERROR", e.getMessage());
        }
    }

    @ReactMethod
    public void changeIcon(String iconName, Promise promise) {
        final Activity activity = getCurrentActivity();
        if (activity == null) {
            Log.e(TAG, "changeIcon: Activity não encontrada");
            promise.reject("ANDROID:ACTIVITY_NOT_FOUND");
            return;
        }

        final String newIconName = (iconName == null || iconName.isEmpty()) ? "Default" : iconName;
        final String currentPackage = reactContext.getPackageName();
        final String activeClass = currentPackage + ".MainActivity" + newIconName;

        Log.d(TAG, "Iniciando mudança de ícone: " + newIconName);
        Log.d(TAG, "Package atual: " + currentPackage);
        Log.d(TAG, "Classe alvo: " + activeClass);

        // Verificação de alias válido
        if (!getValidAliases().contains(newIconName)) {
            Log.e(TAG, "Ícone inválido: " + newIconName);
            promise.reject("ANDROID:INVALID_ICON", "Ícone inválido: " + newIconName);
            return;
        }

        try {
            PackageManager pm = activity.getPackageManager();
            List<String> aliases = getValidAliases();

            Log.d(TAG, "Desativando todos os aliases...");
            // Desativar todos os aliases primeiro
            for (String alias : aliases) {
                String className = currentPackage + ".MainActivity" + alias;
                ComponentName component = new ComponentName(currentPackage, className);
                
                try {
                    // Verificar se o componente existe
                    pm.getActivityInfo(component, 0);
                    Log.d(TAG, "Desativando alias: " + className);
                    
                    pm.setComponentEnabledSetting(
                        component,
                        PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                        PackageManager.DONT_KILL_APP
                    );
                } catch (PackageManager.NameNotFoundException e) {
                    Log.w(TAG, "Alias não encontrado (ignorado): " + className);
                }
            }

            // Ativar o novo alias
            Log.d(TAG, "Ativando novo alias: " + activeClass);
            ComponentName targetComponent = new ComponentName(currentPackage, activeClass);
            
            // Verificar se o componente existe antes de ativar
            try {
                pm.getActivityInfo(targetComponent, 0);
            } catch (PackageManager.NameNotFoundException e) {
                Log.e(TAG, "Componente alvo não encontrado: " + activeClass, e);
                promise.reject("ANDROID:COMPONENT_NOT_FOUND", "Componente não encontrado: " + activeClass);
                return;
            }
            
            pm.setComponentEnabledSetting(
                targetComponent,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
            );

            // Atualizar estado interno
            this.componentClass = activeClass;
            this.iconChanged = true;

            // Atualizar o launcher após um delay
            Log.d(TAG, "Agendando atualização do launcher...");
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                try {
                    Log.d(TAG, "Forçando atualização do launcher");
                    Intent intent = new Intent(Intent.ACTION_MAIN);
                    intent.addCategory(Intent.CATEGORY_HOME);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    activity.startActivity(intent);
                    
                    Log.d(TAG, "Mudança de ícone concluída com sucesso: " + newIconName);
                    promise.resolve(newIconName);
                } catch (Exception e) {
                    Log.e(TAG, "Erro ao atualizar launcher", e);
                    promise.reject("ANDROID:LAUNCHER_UPDATE_FAILED", e.getMessage());
                }
            }, 300);

        } catch (Exception e) {
            Log.e(TAG, "Erro crítico ao mudar ícone", e);
            promise.reject("ANDROID:ICON_CHANGE_FAILED", e.getMessage());
        }
    }

    private void completeIconChange() {
        if (!iconChanged) {
            Log.d(TAG, "completeIconChange: Nenhuma mudança pendente");
            return;
        }
        
        final Activity activity = getCurrentActivity();
        if (activity == null) {
            Log.w(TAG, "completeIconChange: Activity não disponível");
            return;
        }
        
        Log.d(TAG, "Finalizando mudança de ícone...");
        String basePackage = getBasePackageName();
        
        classesToKill.forEach((cls) -> {
            try {
                Log.d(TAG, "Desativando classe: " + cls);
                activity.getPackageManager().setComponentEnabledSetting(
                    new ComponentName(basePackage, cls),
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP
                );
            } catch (Exception e) {
                Log.e(TAG, "Erro ao desativar componente: " + cls, e);
            }
        });
        
        classesToKill.clear();
        iconChanged = false;
        Log.d(TAG, "Processo de mudança de ícone finalizado");
    }

    // Métodos do ciclo de vida (mantidos para compatibilidade)
    @Override
    public void onActivityPaused(Activity activity) {
        completeIconChange();
    }

    @Override
    public void onActivityCreated(Activity activity, Bundle savedInstanceState) {}

    @Override
    public void onActivityStarted(Activity activity) {}

    @Override
    public void onActivityResumed(Activity activity) {}

    @Override
    public void onActivityStopped(Activity activity) {}

    @Override
    public void onActivitySaveInstanceState(Activity activity, Bundle outState) {}

    @Override
    public void onActivityDestroyed(Activity activity) {}
}