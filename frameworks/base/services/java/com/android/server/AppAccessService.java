/* AppAccessService.java */
package com.android.server;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Arrays;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import android.app.Service;
import android.app.AppGlobals;
import android.content.Intent;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.IPackageManager;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Environment;
import android.os.IBinder;
import android.os.Handler;
import android.os.IAppAccessService;
import android.os.Looper;
import android.os.Message;
import android.os.Process;
import android.util.Log;

public class AppAccessService extends IAppAccessService.Stub {

    private static AppAccessService mInstance;
    private AppAccessWorkerThread mWorker;
    private AppAccessWorkerHandler mHandler;
    private Context mContext;
    private File mXmlFile = null;
    private File mPermissionXmlFile = null;
    private final String ANDROID_PERMISSION = "android.permission.";
    private final String PACKAGE = "package";
    private final String UID = "uid";
    private final String APP_ACCESS_LIST = "app-access-list";
    private final String BLOCKED_PERMISSION = "blocked-permission";
    private final String NAME = "name";
    private static final String TAG = "AppAccessService";
    private HashMap<Integer, String> packageUidMap;
    private HashMap<String, ArrayList<String>> packagePermissionsMap;
    private final Map<String, ArrayList<Integer>> permissionGidMap;
    {
        permissionGidMap = new HashMap<String, ArrayList<Integer>>();
        permissionGidMap.put("BLUETOOTH_ADMIN", new ArrayList<Integer>() {
            {
                add(3001);
            }
        });
        permissionGidMap.put("BLUETOOTH", new ArrayList<Integer>() {
            {
                add(3002);
            }
        });
        permissionGidMap.put("INTERNET", new ArrayList<Integer>() {
            {
                add(3003);
            }
        });
        permissionGidMap.put("CAMERA", new ArrayList<Integer>() {
            {
                add(1006);
            }
        });
        permissionGidMap.put("READ_LOGS", new ArrayList<Integer>() {
            {
                add(1007);
            }
        });
        permissionGidMap.put("WRITE_EXTERNAL_STORAGE", new ArrayList<Integer>() {
            {
                add(1015);
            }
        });
        permissionGidMap.put("WRITE_MEDIA_STORAGE", new ArrayList<Integer>() {
            {
                add(1023);
            }
        });
        permissionGidMap.put("ACCESS_MTP", new ArrayList<Integer>() {
            {
                add(1024);
            }
        });
        permissionGidMap.put("NET_ADMIN", new ArrayList<Integer>() {
            {
                add(3005);
            }
        });
        permissionGidMap.put("ACCESS_CACHE_FILESYSTEM", new ArrayList<Integer>() {
            {
                add(2001);
            }
        });
        permissionGidMap.put("DIAGNOSTIC", new ArrayList<Integer>() {
            {
                add(1004);
                add(2002);
            }
        });
        permissionGidMap.put("READ_NETWORK_USAGE_HISTORY", new ArrayList<Integer>() {
            {
                add(3006);
            }
        });
        permissionGidMap.put("MODIFY_NETWORK_ACCOUNTING", new ArrayList<Integer>() {
            {
                add(3007);
            }
        });
    }
    
    private AppAccessService(Context context) {
        super();
        mContext = context;
        mWorker = new AppAccessWorkerThread("AppAccessSerciveWorker");
        mWorker.start();
        initFile();
        Log.i(TAG, "Spawned worker thread");
    }

    public static AppAccessService getInstance(Context context)
    {
        if(context == null && mInstance != null)
            return mInstance;

        if(mInstance == null)
            mInstance = new AppAccessService(context);
                
        return mInstance;
    }

    private class AppAccessWorkerThread extends Thread {
        public AppAccessWorkerThread(String name) {
            super(name);
        }
        public void run() {
            Looper.prepare();
            mHandler = new AppAccessWorkerHandler();
            Looper.loop();
        }
    }

    private class AppAccessWorkerHandler extends Handler {
        private static final int MESSAGE_SET = 0;
        @Override
        public void handleMessage(Message msg) {
            try {
                if (msg.what == MESSAGE_SET) {
                    Log.i(TAG, "set message received: " + msg.arg1);
                }
            } catch (Exception e) {
                // Log, don't crash!
                Log.e(TAG, "Exception in AppAccessWorkerHandler.handleMessage:", e);
            }
        }
    }

    private void addPackageUidToMap(String packageName, int uid) {
        if (packageUidMap == null)
            packageUidMap = new HashMap<Integer, String>();
        packageUidMap.put(uid, packageName);
    }

    private String getPackageNameFromUid(int uid) {
        if (packageUidMap == null) {
            return null;
        }
        else if(packageUidMap.containsKey(uid)) {
            return packageUidMap.get(uid);
        }
        return null;
    }

    private void updatePackagePermissions(String packageName, List<String> permissions) {
        if (packagePermissionsMap == null)
            packagePermissionsMap = new HashMap<String, ArrayList<String>>();
        ArrayList<String> perms = new ArrayList<String>();
        for (String p : permissions) {
            perms.add(p);
        }
        packagePermissionsMap.put(packageName, perms);
    }

    private boolean initFile() {

        packagePermissionsMap = new HashMap<String, ArrayList<String>>();
        packageUidMap = new HashMap<Integer, String>();

        File dataDir = Environment.getDataDirectory();
        File systemDir = new File(dataDir, "system");
        //File dataDir = new File(getApplicationInfo().dataDir);
        mXmlFile = new File(systemDir, "acl.xml");
        try {
            if (!mXmlFile.exists()) {
                mXmlFile.createNewFile();
            }
            Log.i(TAG, "XML File created " + mXmlFile.getAbsolutePath());
            DocumentBuilderFactory docFactory = DocumentBuilderFactory
                    .newInstance();
            DocumentBuilder docBuilder = docFactory.newDocumentBuilder();

            // root element
            Document doc = docBuilder.newDocument();
            Element rootElement = doc.createElement(APP_ACCESS_LIST);
            doc.appendChild(rootElement);

            // write the content into xml file
            TransformerFactory transformerFactory = TransformerFactory
                    .newInstance();
            Transformer transformer = transformerFactory.newTransformer();
            DOMSource source = new DOMSource(doc);
            StreamResult result = new StreamResult(mXmlFile);

            // Output to console for testing
            // StreamResult result = new StreamResult(System.out);

            transformer.transform(source, result);
            BufferedReader br = new BufferedReader(new FileReader(mXmlFile));
            String line = null;
            while ((line = br.readLine()) != null) {
                Log.i(TAG, line);
            }
            br.close();
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            Log.e(TAG, "Unable to create acl.xml");
            return false;
        } catch (ParserConfigurationException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            Log.e(TAG, "Error Parsing");
            return false;
        } catch (TransformerConfigurationException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            Log.e(TAG, "Error Transforming");
            return false;
        } catch (TransformerException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            Log.e(TAG, "Error Transforming");
            return false;
        }
    }

    public boolean updateBlockedPermissions(String pkgName, int uid,
             List<String> blockedPermissions) {
        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder dBuilder;
        try {
            dBuilder = dbFactory.newDocumentBuilder();
            Document doc = dBuilder.parse(mXmlFile);
            doc.getDocumentElement().normalize();
            Element rootElement = doc.getDocumentElement();
            NodeList packageList = doc.getElementsByTagName(PACKAGE);
            Element mPackage = null;
            boolean isPackagePresent = false;
            for (int i = 0; i < packageList.getLength(); i++) {
                Node nNode = packageList.item(i);
                if (nNode.getNodeType() == Node.ELEMENT_NODE) {
                    Element eElement = (Element) nNode;
                    if (eElement.getAttribute(NAME).equalsIgnoreCase(pkgName)) {
                        //mPackage = eElement;
                        //isPackagePresent = true;
                        // Edit - remove Node
                        rootElement.removeChild(nNode);
                        break;                        
                    }
                }
            }
            mPackage = doc.createElement(PACKAGE);
            // packageName
            Attr packageAttr = doc.createAttribute(NAME);
            packageAttr.setValue(pkgName);
            mPackage.setAttributeNode(packageAttr);
            // uid
            Attr uidAttr = doc.createAttribute(UID);
            uidAttr.setValue(String.valueOf(uid));
            mPackage.setAttributeNode(uidAttr);
            rootElement.appendChild(mPackage);
            addPackageUidToMap(pkgName, uid);
            NodeList permissionList = mPackage
                    .getElementsByTagName(BLOCKED_PERMISSION);
            
            // Every time new permissions to be added to blocked list
            for (String perm : blockedPermissions) {
                Element newPermission = doc.createElement(BLOCKED_PERMISSION);
                newPermission.appendChild(doc.createTextNode(perm));
                mPackage.appendChild(newPermission);
            }
            updatePackagePermissions(pkgName, blockedPermissions);

            TransformerFactory transformerFactory = TransformerFactory
                    .newInstance();
            Transformer transformer = transformerFactory.newTransformer();
            DOMSource source = new DOMSource(doc);
            StreamResult result = new StreamResult(mXmlFile);

            // Output to console for testing
            // StreamResult result = new StreamResult(System.out);

            transformer.transform(source, result);
            Log.i(TAG, "Xml file updated");
            BufferedReader br = new BufferedReader(new FileReader(mXmlFile));
            String line = null;
            while ((line = br.readLine()) != null) {
                Log.i(TAG, line);
            }
            br.close();
            return true;
        } catch (ParserConfigurationException e) {
            e.printStackTrace();
            return false;
        } catch (SAXException e) {
            e.printStackTrace();
            return false;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        } catch (TransformerConfigurationException e) {
            e.printStackTrace();
            return false;
        } catch (TransformerException e) {
            e.printStackTrace();
            return false;
        }
    }

    public ArrayList<String> getBlockedPermissions(String pkgName) {
        if (packagePermissionsMap == null) {
            return new ArrayList<String>();
        }
        else if (!packagePermissionsMap.containsKey(pkgName)) {
            return new ArrayList<String>();
        }
        else {
            return packagePermissionsMap.get(pkgName);
        }
    }

    /**
     * Returns a List of gids after mapping the blocked permissions with gids.
     * 
     * If there are no blocked permissions or blocked gids empty list is returned
     *
     * @param  packageName the packageName of the app
     * @return blockedGids the list of blocked gids
     */
    public int[] getBlockedGids(String packageName) {
        List<Integer> blockedGids = new ArrayList<Integer>();
        ArrayList<String> blockedPermissions = getBlockedPermissions(packageName);
        for(String permission : blockedPermissions) {
            if(permissionGidMap.containsKey(permission)) {
                List<Integer> gidsForPermission = permissionGidMap.get(permission);
                for(Integer gid : gidsForPermission) {
                    blockedGids.add(gid);
                }
            }
        }
        return convertIntegerListToArray(blockedGids);
    }

    private int[] convertIntegerListToArray(List<Integer> integers)
    {
        int[] arr = new int[integers.size()];
        Iterator<Integer> iterator = integers.iterator();
        for (int i = 0; i < arr.length; i++)
        {
            arr[i] = iterator.next().intValue();
        }
        return arr;
    }

    public boolean isPermissionBlocked(String permission, int uid) {
        permission = permission.replace(ANDROID_PERMISSION, "");
        //Log.e(TAG, "Checking " + permission + " for process with uid " + uid );
        String pkgName = getPackageNameFromUid(uid);
        if(pkgName == null) {
            //Log.e(TAG, "False - pkgNameUidMap not present");
            return false;
        }
        if (packagePermissionsMap == null) {
            //Log.e(TAG, "False - perm map not initialized");
            return false;
        }
        else if (!packagePermissionsMap.containsKey(pkgName)) {
            //Log.e(TAG, "False - perm map has no entry for process");
            return false;
        }
        else {
            if(packagePermissionsMap.get(pkgName).contains(permission)) {
                Log.i(TAG, "*** PERMISSION BLOCKED *** Permission: "+ permission + ", Process: " + pkgName);
            }
            return packagePermissionsMap.get(pkgName).contains(permission);
        }
    }

    public void blockPermissionRedelegation(String callerApp, String calleeApp, List<String> callerPermissions, List<String> calleePermissions) {
        Log.i(TAG,  "Caller permissions: "+ callerPermissions);
        Log.i(TAG,  "Callee permissions: "+ calleePermissions);
        Log.i(TAG, "PermissionGidMap: " + permissionGidMap);
        List<Integer> callerGids = new ArrayList<Integer>();
        List<Integer> calleeGids = new ArrayList<Integer>();

        // Extract caller gids from permissions
        /*for (String callerPermission : callerPermissions) {
            String permission = callerPermission.replace(ANDROID_PERMISSION, "");
            Log.i(TAG, permission);
            if (permissionGidMap.containsKey(permission)) {
                ArrayList<Integer> gids = permissionGidMap.get(permission);
                for (Integer gid : gids) {
                    callerGids.add(gid);
                }
            }
        }

        Log.i(TAG, "Caller gids: " + callerGids);

        // Extract callee gids from permissions
        for (String calleePermission : calleePermissions) {
            String permission = calleePermission.replace(ANDROID_PERMISSION, "");
            if (AppAccessService.permissionGidMap.containsKey(permission)) {
                ArrayList<Integer> gids = AppAccessService.permissionGidMap.get(permission);
                for (Integer gid : gids) {
                    calleeGids.add(gid);
                }
            }
        }

        Log.i(TAG, "Callee gids: " + calleeGids);

        // Blocked callee gids = calleeGids - callerGids
        List<Integer> blockedCalleeGids = new ArrayList<Integer>();
        for (Integer calleeGid : calleeGids) {
            if (!callerGids.contains(calleeGid)) {
                blockedCalleeGids.add(calleeGid);
            }
        }

        Log.i(TAG, "Blocked callee gids: " + blockedCalleeGids);*/

        List<String> blockedCalleePermissions = new ArrayList<String>();
        for (String calleePermission : calleePermissions) {
            if (!callerPermissions.contains(calleePermission)) {
                blockedCalleePermissions.add(calleePermission);
            }
        }

        Log.i(TAG, "Blocked callee permissions: " + blockedCalleePermissions);
    }
}