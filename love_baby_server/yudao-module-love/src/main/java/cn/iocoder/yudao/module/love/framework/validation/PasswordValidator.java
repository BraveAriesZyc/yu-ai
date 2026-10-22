package cn.iocoder.yudao.module.love.framework.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * {@link Password} 校验器：6-16 位 + 同时含字母与数字。
 * 用 Character 遍历不用正则——无状态编译、语义直白（与仓内校验工具风格一致）。
 */
public class PasswordValidator implements ConstraintValidator<Password, String> {

    /**
     * null/空串放行（交 @NotEmpty 出「密码不能为空」文案），与 yudao-common MobileValidator 同款约定
     */
    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isEmpty()) {
            return true;
        }
        if (value.length() < 6 || value.length() > 16) {
            return false;
        }
        boolean hasLetter = false;
        boolean hasDigit = false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')) {
                hasLetter = true;
            } else if (c >= '0' && c <= '9') {
                hasDigit = true;
            }
        }
        return hasLetter && hasDigit;
    }

}
