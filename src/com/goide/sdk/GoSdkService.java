/*
 * Copyright 2013-2015 Sergey Ignatov, Alexander Zolotov, Mihai Toader, Florin Patan
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.goide.sdk;

import com.goide.GoConstants;
import com.goide.GoEnvironmentUtil;
import com.intellij.execution.configurations.PathEnvironmentVariableUtil;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SimpleModificationTracker;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.PathUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Set;

public abstract class GoSdkService extends SimpleModificationTracker {
  private static final Set<String> FEDORA_SUBDIRECTORIES = ContainerUtil.newHashSet("linux_amd64", "linux_386", "linux_arm");
  
  @NotNull
  protected final Project myProject;

  protected GoSdkService(@NotNull Project project) {
    myProject = project;
  }

  public static GoSdkService getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, GoSdkService.class);
  }

  @Nullable
  public abstract String getSdkHomePath(@Nullable Module module);

  @NotNull
  public static String libraryRootToSdkPath(@NotNull VirtualFile root) {
    return VfsUtilCore.urlToPath(StringUtil.trimEnd(StringUtil.trimEnd(StringUtil.trimEnd(root.getUrl(), "src/pkg"), "src"), "/"));
  }

  @Nullable
  public abstract String getSdkVersion(@Nullable Module module);

  public boolean isAppEngineSdk(@Nullable Module module) {
    return isAppEngineSdkPath(getSdkHomePath(module));
  }

  public static boolean isAppEngineSdkPath(@Nullable String path) {
    return path != null && path.endsWith(GoConstants.APP_ENGINE_GO_ROOT_DIRECTORY_PATH);
  }

  public abstract void chooseAndSetSdk(@Nullable Module module);

  /**
   * Use this method in order to check whether the method is appropriate for providing Go-specific code insight
   */
  @Contract("null -> false")
  public boolean isGoModule(@Nullable Module module) {
    return module != null && !module.isDisposed();
  }

  @Nullable
  public Configurable createSdkConfigurable() {
    return null;
  }

  @Nullable
  public String getGoExecutablePath(@Nullable Module module) {
    return getGoExecutablePath(getSdkHomePath(module));
  }

  public static String getGoExecutablePath(@Nullable String sdkHomePath) {
    if (sdkHomePath != null) {
      if (isAppEngineSdkPath(sdkHomePath)) {
        String goExecutablePath = PathUtil.toSystemIndependentName(sdkHomePath);
        goExecutablePath = StringUtil.trimEnd(goExecutablePath, GoConstants.APP_ENGINE_GO_ROOT_DIRECTORY_PATH);
        
        boolean gcloudInstallation = goExecutablePath.endsWith(GoConstants.GCLOUD_APP_ENGINE_DIRECTORY_PATH);
        if (gcloudInstallation) {
          goExecutablePath = FileUtil.join(StringUtil.trimEnd(goExecutablePath, GoConstants.GCLOUD_APP_ENGINE_DIRECTORY_PATH), "bin");
        }
        return FileUtil.join(goExecutablePath, GoEnvironmentUtil.getGaeExecutableFileName(gcloudInstallation));
      }
      else {
        File binDirectory = new File(sdkHomePath, "bin");
        if (!binDirectory.exists() && SystemInfo.isLinux) {
          // failed to define executable path in old linux and old go
          File goFromPath = PathEnvironmentVariableUtil.findInPath(GoConstants.GO_EXECUTABLE_NAME);
          if (goFromPath != null && goFromPath.exists()) return goFromPath.getAbsolutePath();
        }

        String executableName = GoEnvironmentUtil.getBinaryFileNameForPath(GoConstants.GO_EXECUTABLE_NAME);
        String executable = FileUtil.join(sdkHomePath, "bin", executableName);
        
        if (!new File(executable).exists() && SystemInfo.isLinux) {
          // fedora
          for (String directory : FEDORA_SUBDIRECTORIES) {
            File file = new File(binDirectory, directory);
            if (file.exists() && file.isDirectory()) return FileUtil.join(file.getAbsolutePath(), executableName);
          }
        }
        return executable;
      }
    }
    return null;
  }
}
