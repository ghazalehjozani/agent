package com.example.ai01.agent.model;

public record ViolationFinding(
        String standard,        // مثل "SAW-101" — کد استاندارد سازمانی
        String domain,          // مثل "rest-url" — حوزه‌ی قانون
        String ruleId,          // مثل "REST-URL-001" — شناسه‌ی قانون نقض‌شده
        String severity,        // BLOCKER | MAJOR | MINOR | INFO
        String file,            // مسیر فایل متخلف
        Integer line,           // شماره خط (nullable — برای قوانین ساختاری که خط ندارند)
        String evidence,            // شواهد فنی نقض قانون
        String recommendation,  // پیشنهاد اصلاح
        Double confidence       // میزان اطمینان بین 0.0 تا 1.0
) {
}