package net.henryhc.mocksniffer.testinstrument.sourceresources

val logScript = """
package tool;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.UUID;
import java.net.Socket;

public class MockLogger {

    static boolean isMockitoAvailable = false;
    static Method mockingDetailMethod;

    static Method isMockMethod;
    static Method isSpyMethod;
    
    static String redisHost = "<redis_host>";
    static int redisPort = <redis_port>;
    
    static Redis redisClient;

    static {
        try {
            redisClient = new Redis(new Socket(redisHost, redisPort));
            Class mockitoClass = Class.forName("org.mockito.Mockito");
            mockingDetailMethod = mockitoClass.getMethod("mockingDetails", Object.class);
            Class mockingDetailClass = Class.forName("org.mockito.MockingDetails");
            isMockMethod = mockingDetailClass.getMethod("isMock");
            isSpyMethod = mockingDetailClass.getMethod("isSpy");
            isMockitoAvailable = true;
        } catch (ClassNotFoundException | NoSuchMethodException ignored) {
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    public static <T> void recordObj(T obj, int paramIdx, String methodSignature) {
        if (obj == null)
            return;
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        String typeName = obj.getClass().getTypeName();
        String objType = "real";
        if (isSpy(obj))
            objType = "spy";
        else if (isMock(obj))
            objType = "mock";
        writeData("obj", typeName, methodSignature, stackTrace, objType, paramIdx);
    }

    public static void recordInv(Object obj, String methodSignature) {
        String typeName = obj.getClass().getTypeName();
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        String objType = "real";
        if (isSpy(obj))
            objType = "spy";
        else if (isMock(obj))
            objType = "mock";
        writeData("inv", typeName, methodSignature, stackTrace, objType, -1);
    }

    public static void recordNew(String typeName) {
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        writeData("new", typeName, "", stackTrace, "new", -1);
    }

    private static boolean isMock(Object obj) {
        if (isMockitoAvailable) {
            try {
                Object mockingDetail = mockingDetailMethod.invoke(null, obj);
                return (Boolean) isMockMethod.invoke(mockingDetail);
            } catch (IllegalAccessException | InvocationTargetException ignored) {
            }
        }
        return false;
    }

    private static boolean isSpy(Object obj) {
        if (isMockitoAvailable) {
            try {
                Object mockingDetail = mockingDetailMethod.invoke(null, obj);
                return (Boolean) isSpyMethod.invoke(mockingDetail);
            } catch (IllegalAccessException | InvocationTargetException ignored) {
            }
        }
        return false;
    }

    private static void writeData(String type, String typeName, String methodSignature, StackTraceElement[] stackTrace, String objType, int paramIdx) {
        StringBuilder sb = new StringBuilder();
        sb.append(objType);
        sb.append("\n");
        sb.append(typeName);
        sb.append("\n");
        sb.append(paramIdx);
        sb.append("\n");
        if (type.equals("inv") || type.equals("obj")) {
            sb.append(methodSignature);
            sb.append("\n");
        }
        for (int i = 2; i < stackTrace.length; i++) {
            StackTraceElement ele = stackTrace[i];
            if (
                    ele.getLineNumber() == -2
                            && ele.getMethodName().equals("invoke0")
                            && ele.getFileName().equals("NativeMethodAccessorImpl.java")
                            && ele.getClassName().equals("sun.reflect.NativeMethodAccessorImpl")
            ) {
                break;
            }
            String line = String.format("%s,%s,%s,%s\n",
                    ele.getFileName(),
                    ele.getLineNumber(),
                    ele.getClassName(),
                    ele.getMethodName());
            sb.append(line);
        }
        saveRedis(type, sb.toString());
    }
    
    private static void saveRedis(String type, String content) {
//        String keyName = String.format("%s:%s", type, UUID.randomUUID().toString());
        try {
            redisClient.call("SADD", type, content);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

""".trimIndent()
