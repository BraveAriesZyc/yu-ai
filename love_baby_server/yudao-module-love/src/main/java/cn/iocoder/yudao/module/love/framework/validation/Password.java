package cn.iocoder.yudao.module.love.framework.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;

/**
 * 密码强度校验（M20）：6-16 位，且必须同时包含字母与数字。
 * 校验落点仅注册/重置密码两个入口（login VO 不挂——老弱密码存量用户照常登录）；
 * null/空串不校验（交 @NotEmpty 出「不能为空」文案），与 yudao-common Mobile 同款约定。
 * 违规经全局异常处理器包在 HTTP 200 body code=400（与 @Pattern 同路径）。
 */
@Target({
        ElementType.METHOD,
        ElementType.FIELD,
        ElementType.ANNOTATION_TYPE,
        ElementType.CONSTRUCTOR,
        ElementType.PARAMETER,
        ElementType.TYPE_USE
})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Constraint(
        validatedBy = PasswordValidator.class
)
public @interface Password {

    String message() default "密码须为 6-16 位，且同时包含字母和数字";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

}
