import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.apache.commons.lang3.StringUtils;
import org.apache.maven.model.License;
import org.apache.maven.model.Model;
import org.apache.maven.model.Parent;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.codehaus.plexus.util.FileUtils;

public class DependenceService {

    private static final String CURRENT_DIR = System.getProperty("user.dir") + File.separator;//根目录
    private static final String sdkdepens_path = CURRENT_DIR + "sdkdepens.txt";//被检测的依赖文件（默认在根目录）
    private static final String sdkresult_path = CURRENT_DIR + "sdkresult.txt";//检测结果
    private static final String gradle_repositories_path = "H:\\.gradle\\caches\\modules-2\\files-2.1";//gradle的本地maven仓库
    private static Map<String, Set<String>> jarMap = new HashMap<>();//所有的jar
    private static Set<String> jarHadNotpom = new TreeSet<>();//在本地仓库没找到pom文件的jar


    /**
     * 检测开源协议
     */
    public static void main(String args[]) throws Exception {
        BufferedReader bis = new BufferedReader(new FileReader(sdkdepens_path));
        String line = null;
        StringBuffer buffStr = new StringBuffer();
        while ((line = bis.readLine()) != null) {
            if (line.indexOf("+---") >= 0 || line.indexOf("\\---") >= 0) {
                buffStr.append(line);
                String[] strs = line.split("---");
                String jarName = strs[1];
                jarName = jarName.replaceAll("\\(\\*\\)", "").trim();
                if (jarName.contains("->")) {
                    //如果有"->"标志，则只获取高版本license
                    jarName = jarName.replaceAll(" ", "");
                    int subIndex = jarName.indexOf("->");
                    String jarVersionUpper = jarName.substring(subIndex + 2);
                    jarName = jarName.substring(0, jarName.lastIndexOf(":") + 1) + jarVersionUpper;
                }
                //获取开源协议
                putJarLicense2Map(jarName, getLicense(jarName));
            }
        }

        //打印set的内容
        jarHadNotpom.stream().forEach(jar -> System.err.println(jar));

        //打印map的内容
        printJarMap(jarMap);
    }


    /**
     * 打印jar包map
     */
    private static void printJarMap(Map<String, Set<String>> jarMap) throws IOException {
        StringBuffer buffStr = new StringBuffer();
        Set<String> licenseSet = jarMap.keySet();
        for (String license : licenseSet) {

            buffStr.append(
                "============================================================================================================\r\n");
            buffStr.append("*** " + license + " ***:\r\n");
            Set<String> jarList = jarMap.get(license);
            jarList.stream().forEach(jarName -> buffStr.append(jarName).append("\r\n"));
            buffStr.append("\r\n");
        }

        String allJar = buffStr.toString();

        //结果打印到控制台
        System.out.println(allJar);
        //结果写入到文件
        FileUtils.fileWrite(new File(sdkresult_path), allJar);
    }


    /**
     * 获取开源协议
     */
    private static String getLicense(String jarName) throws Exception {

        String[] jarInfoArr = jarName.split(":");
        String groupId = jarInfoArr[0];
        String artifactId = jarInfoArr[1];
        String version = jarInfoArr[2];
        //jar包的跟路径，如：H:\.gradle\caches\modules-2\files-2.1\org.springframework.boot\spring-boot\2.1.1.RELEASE
        String jarBasePath =
            gradle_repositories_path + File.separator + groupId + File.separator + artifactId
                + File.separator + version;

        File jarBase = new File(jarBasePath);
        if (!jarBase.exists()) {
            //保存jar包名到set
            jarHadNotpom.add(jarName);
            return null;
        }

        String pomFileName = artifactId + "-" + version + ".pom";//pom文件名
        File pomFiles = getPomFile(jarBase, pomFileName);//pom文件
        if(pomFiles==null){
            //保存jar包名到set
            jarHadNotpom.add(jarName);
            return null;
        }
        return getLicenseFromPomFile(pomFiles);//从pom文件读取开源协议
    }


    private static void putJarLicense2Map(String jarName, String license) {
        if (StringUtils.isBlank(license)) {
            license = "noLicense";
        }
        if (jarMap.containsKey(license)) {
            jarMap.get(license).add(jarName);
        } else {
            Set<String> jarSet = new HashSet<>();
            jarSet.add(jarName);
            jarMap.put(license, jarSet);
        }
    }

    /**
     * 获取pom文件，如 传入H:\.gradle\caches\modules-2\files-2.1\org.mybatis.spring.boot\mybatis-spring-boot-starter\1.3.2
     * 返回：文件mybatis-spring-boot-starter-1.3.2.pom
     */
    private static File getPomFile(File parentDir, String pomName) {
        File[] fileList = parentDir.listFiles();
        for (File file : fileList) {
            if (pomName.equals(file.getName())) {
                return file;
            }
            if (file.isDirectory()) {
                File pomFile = getPomFile(file, pomName);
                if (pomFile != null) {
                    return pomFile;
                }
            }
        }
        return null;
    }

    /**
     * 从pom文件中获取开源协议
     */
    private static String getLicenseFromPomFile(File pomFile) throws Exception {
        FileInputStream fis = new FileInputStream(pomFile);
        MavenXpp3Reader reader = new MavenXpp3Reader();
        Model model = reader.read(fis);
        List<License> licenseList = model.getLicenses();
        Parent parentPom = model.getParent();
        if (licenseList.size() > 0) {
            License license = licenseList.get(0);
            return license.getName();
        } else if (parentPom != null) {
            //如果当前pom文件没有license,就从父依赖获取
            String groupId = parentPom.getGroupId();
            String artifactId = parentPom.getArtifactId();
            String version = parentPom.getVersion();
            String parentJarName = groupId + ":" + artifactId + ":" + version;
            String license = getLicense(parentJarName);
            return license;
        }
        return null;
    }
}
