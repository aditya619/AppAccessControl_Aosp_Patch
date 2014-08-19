/*
* aidl file : frameworks/base/core/java/android/os/IAppAccessService.aidl
* This file contains definitions of functions which are exposed by service
* AppAccessService 
*/
package android.os;

import java.util.List;

interface IAppAccessService {
/**
* {@hide}
*/
	List<String> getBlockedPermissions(String pkgName);
	boolean updateBlockedPermissions(String pkgName, int uid, in List<String> blockedPermissions);
	boolean isPermissionBlocked(String permission, int uid);
	int[] getBlockedGids(String packageName);
	void blockPermissionRedelegation(String callerApp, String calleeApp, in List<String> callerPermissions, in List<String> calleePermissions, int calleeUid);
}
