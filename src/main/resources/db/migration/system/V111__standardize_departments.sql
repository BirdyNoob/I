-- V36__standardize_departments.sql
-- Migrate free-form text to exact ENUM strings

UPDATE tenant_users SET department = 'ENGINEERING' WHERE LOWER(department) = 'engineering';
UPDATE tenant_users SET department = 'SALES' WHERE LOWER(department) = 'sales';
UPDATE tenant_users SET department = 'MARKETING' WHERE LOWER(department) = 'marketing';
UPDATE tenant_users SET department = 'LEGAL' WHERE LOWER(department) = 'legal';
UPDATE tenant_users SET department = 'PRODUCT' WHERE LOWER(department) = 'product';
UPDATE tenant_users SET department = 'MANAGEMENT' WHERE LOWER(department) = 'management';
UPDATE tenant_users SET department = 'OPERATIONS' WHERE LOWER(department) = 'operations';
UPDATE tenant_users SET department = 'QUALITY_ASSURANCE' WHERE LOWER(department) = 'quality assurance';
UPDATE tenant_users SET department = 'CUSTOMER_SUCCESS' WHERE LOWER(department) = 'customer success';
UPDATE tenant_users SET department = 'BUSINESS_DEVELOPMENT' WHERE LOWER(department) = 'business development';
UPDATE tenant_users SET department = 'FINANCE' WHERE LOWER(department) = 'finance';
UPDATE tenant_users SET department = 'HUMAN_RESOURCES' WHERE LOWER(department) = 'human resources';
UPDATE tenant_users SET department = 'IT_INFORMATION_SECURITY' WHERE LOWER(department) IN ('it / information security', 'it/information security', 'information security');
UPDATE tenant_users SET department = 'COMMUNICATIONS_PR' WHERE LOWER(department) IN ('communications / pr', 'communications/pr', 'communications', 'pr');
UPDATE tenant_users SET department = 'FACILITIES' WHERE LOWER(department) = 'facilities';
UPDATE tenant_users SET department = 'PROCUREMENT' WHERE LOWER(department) = 'procurement';


UPDATE tracks SET department = 'ENGINEERING' WHERE LOWER(department) = 'engineering';
UPDATE tracks SET department = 'SALES' WHERE LOWER(department) = 'sales';
UPDATE tracks SET department = 'MARKETING' WHERE LOWER(department) = 'marketing';
UPDATE tracks SET department = 'LEGAL' WHERE LOWER(department) = 'legal';
UPDATE tracks SET department = 'PRODUCT' WHERE LOWER(department) = 'product';
UPDATE tracks SET department = 'MANAGEMENT' WHERE LOWER(department) = 'management';
UPDATE tracks SET department = 'OPERATIONS' WHERE LOWER(department) = 'operations';
UPDATE tracks SET department = 'QUALITY_ASSURANCE' WHERE LOWER(department) = 'quality assurance';
UPDATE tracks SET department = 'CUSTOMER_SUCCESS' WHERE LOWER(department) = 'customer success';
UPDATE tracks SET department = 'BUSINESS_DEVELOPMENT' WHERE LOWER(department) = 'business development';
UPDATE tracks SET department = 'FINANCE' WHERE LOWER(department) = 'finance';
UPDATE tracks SET department = 'HUMAN_RESOURCES' WHERE LOWER(department) = 'human resources';
UPDATE tracks SET department = 'IT_INFORMATION_SECURITY' WHERE LOWER(department) IN ('it / information security', 'it/information security', 'information security');
UPDATE tracks SET department = 'COMMUNICATIONS_PR' WHERE LOWER(department) IN ('communications / pr', 'communications/pr', 'communications', 'pr');
UPDATE tracks SET department = 'FACILITIES' WHERE LOWER(department) = 'facilities';
UPDATE tracks SET department = 'PROCUREMENT' WHERE LOWER(department) = 'procurement';

UPDATE cheat_sheets SET department = 'ENGINEERING' WHERE LOWER(department) = 'engineering';
UPDATE cheat_sheets SET department = 'SALES' WHERE LOWER(department) = 'sales';
UPDATE cheat_sheets SET department = 'MARKETING' WHERE LOWER(department) = 'marketing';
UPDATE cheat_sheets SET department = 'LEGAL' WHERE LOWER(department) = 'legal';
UPDATE cheat_sheets SET department = 'PRODUCT' WHERE LOWER(department) = 'product';
UPDATE cheat_sheets SET department = 'MANAGEMENT' WHERE LOWER(department) = 'management';
UPDATE cheat_sheets SET department = 'OPERATIONS' WHERE LOWER(department) = 'operations';
UPDATE cheat_sheets SET department = 'QUALITY_ASSURANCE' WHERE LOWER(department) = 'quality assurance';
UPDATE cheat_sheets SET department = 'CUSTOMER_SUCCESS' WHERE LOWER(department) = 'customer success';
UPDATE cheat_sheets SET department = 'BUSINESS_DEVELOPMENT' WHERE LOWER(department) = 'business development';
UPDATE cheat_sheets SET department = 'FINANCE' WHERE LOWER(department) = 'finance';
UPDATE cheat_sheets SET department = 'HUMAN_RESOURCES' WHERE LOWER(department) = 'human resources';
UPDATE cheat_sheets SET department = 'IT_INFORMATION_SECURITY' WHERE LOWER(department) IN ('it / information security', 'it/information security', 'information security');
UPDATE cheat_sheets SET department = 'COMMUNICATIONS_PR' WHERE LOWER(department) IN ('communications / pr', 'communications/pr', 'communications', 'pr');
UPDATE cheat_sheets SET department = 'FACILITIES' WHERE LOWER(department) = 'facilities';
UPDATE cheat_sheets SET department = 'PROCUREMENT' WHERE LOWER(department) = 'procurement';
