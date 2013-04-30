/**
 * https://github.com/smtp2006/bean-validate-plugin.git 
 */
package com.github.smtp2006.bean.validate;

import static org.junit.Assert.assertNotNull;

import java.io.FileNotFoundException;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.smtp2006.bean.validate.rule.Rule;

/**
 * @author wanghua
 * @version 2013-4-22 下午9:07:30
 * 
 */
public class ClassValidators {
    // ------------------------------------------------------ Static Variables
    /**
     * logger.
     */
    private static final Logger logger = LoggerFactory.getLogger(ClassValidators.class);
    /**
     * 代理.
     */
    private  ClassValidatorLoader loader;
    /**
     * Class:JavaBean.Class,<Key>:ClassValidator#namespace,<Value>:ClassValidator.
     */
    private final ConcurrentMap<Class<?>, Map<String, ClassValidator<?>>> classValidatorCache = new ConcurrentHashMap<Class<?>, Map<String, ClassValidator<?>>>();
    /**
     * <Key>:JavaBean.Class,<Value>:Lock.
     */
    private final ConcurrentMap<Class<?>, Boolean> classLocks = new ConcurrentHashMap<Class<?>, Boolean>();
    /**
     * 
     */
    private ConcurrentMap<String, MessageFormat> messageFormatCache = new ConcurrentHashMap<String, MessageFormat>();
    public  ClassValidators() {
        loader = new ClassValidatorLoader();
    }
    
    public  ClassValidators(ClassValidatorLoader loader) {
        this. loader = loader;
    }
 
    @SuppressWarnings("rawtypes")
    private boolean hasLoaded(Class klass) {
        Boolean lock = classLocks.get(klass);
        if(lock == null) {
            lock = Boolean.FALSE;
            classLocks.putIfAbsent(klass, lock);
        }
        return lock;
    }
    @SuppressWarnings("rawtypes")
    private Map<String, ClassValidator<?>> getClassValidators(Class klass) throws Exception {
        Boolean loaded = hasLoaded(klass);
        Map<String, ClassValidator<?>> classValidators = null;
        if(loaded) {
            // get from cache
            classValidators = classValidatorCache.get(klass);
            if (classValidators == null) {
                // if has null value, throw exception
                String error = "There is no ClassValidator for " + klass.getName();
                logger.error(error);
                throw new FileNotFoundException(error);
            }
            
        } else {
            // try to load class.xml from classpath
            String url = klass.getName().replaceAll("\\.", "/") + ClassValidatorLoader.SUFFIX;
            logger.debug("try to loadClassValidator({})", url);
            synchronized (loaded) {
                classValidators = loader.loadClassValidator(klass);
            }
        }
        return classValidators;
    }
    
    private Map<String, String> formatResult(Map<String, List<Rule>> validateResult) {
        Map<String, String> ret = null;
        if (validateResult != null && !validateResult.isEmpty()) {
            ret = new HashMap<String, String>(validateResult.size());

            for (Map.Entry<String, List<Rule>> entry : validateResult.entrySet()) {
                StringBuilder propertyResult = new StringBuilder();
                for (Rule rule : entry.getValue()) {
                    if (propertyResult.length() > 0) {
                        propertyResult.append(";");
                    }
                    MessageFormat mf = messageFormatCache.get(rule.format());
                    if (mf == null) {
                        mf = new MessageFormat(rule.format());
                        messageFormatCache.putIfAbsent(rule.format(), mf);
                    }

                    propertyResult.append(mf.format(new Object[] {entry.getKey() }));
                }
                ret.put(entry.getKey(), propertyResult.toString());
            }
        }
        return ret;
    }
    /**
     * 
     * @version 2013-4-29 上午2:09:21
     * @param obj 校验对象
     * @param namespace 指定命名空间校验规则
     * @return 校验失败的消息
     * @throws Exception 加载规则文件异常
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public Map<String, String> validate(Object obj, String namespace) throws Exception {
        assertNotNull(obj);

        Map<String, String> ret = null;
        Map<String, ClassValidator<?>> classValidators = getClassValidators(obj.getClass());
        
        ClassValidator classValidator = classValidators.get(obj.getClass().getName() + "#" + namespace);
        if (classValidator == null) {
            String error = "Can`t find namespace = " + namespace + " with class " + obj.getClass().getName();
            logger.error(error);
            throw new IllegalArgumentException(error);
        } else {
            Map<String, List<Rule>> validateResult = classValidator.validate(obj);
            ret = formatResult(validateResult);
        }
        return ret;
    }
    /**
     * @param obj 需要校验的对象
     * @return 按默认Class.getName() + "#default"命名空间对应的ClassValidator校验，校验失败返回错误提示
     * @throws Exception 运行时异常和Digester异常
     */
    public Map<String, String> validate(Object obj) throws Exception {
        return validate(obj, ClassValidator.DEFAULT_NAME);
    }
}
