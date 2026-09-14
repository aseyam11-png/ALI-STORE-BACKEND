package com.store; // 1. تحديد الـ Package في السطر الأول

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import com.store.MessageService.NotificationManager; // 2. استيراد NotificationManager

@SpringBootApplication
public class StoreApplication { // 3. تعديل اسم الكلاس ليكون مطابقاً لاسم الملف StoreApplication

    public static void main(String[] args) {
        // تشغيل Spring Container
        ApplicationContext context = SpringApplication.run(StoreApplication.class, args);

        // جلب الـ Bean واستدعاء الدالة
        NotificationManager manager = context.getBean(NotificationManager.class);
        manager.notifyUser("ahmed@example.com", "مرحباً بك في دورة Spring!");
    }
}

//package com.store;
//
//import org.springframework.boot.SpringApplication;
//import org.springframework.boot.autoconfigure.SpringBootApplication;
//import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
//
//@SpringBootApplication
//@EnableJpaAuditing
//public class StoreApplication {
//
//    public static void main(String[] args) {
//        SpringApplication.run(StoreApplication.class, args);
//    }
//}