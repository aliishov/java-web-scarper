package org.raul.javawebscarper.service.logging;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.raul.javawebscarper.dto.common.PageResponseDTO;
import org.raul.javawebscarper.model.Author;
import org.raul.javawebscarper.model.Keyword;
import org.raul.javawebscarper.model.Post;
import org.raul.javawebscarper.model.ScrapeJob;
import org.raul.javawebscarper.model.Source;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.lang.reflect.Array;
import java.lang.reflect.RecordComponent;
import java.time.temporal.TemporalAccessor;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;

@Slf4j
@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class ServiceOperationLoggingAspect {

	private static final Set<String> SAFE_RECORD_COMPONENTS = Set.of(
			"id", "sourceId", "keywordId", "authorId", "status", "runType", "role", "enabled",
			"sourcesCount", "keywordsCount", "jobsCreated", "jobsSucceeded", "jobsFailed", "jobsSkipped",
			"postsFound", "postsSaved", "postsReceived", "postsCreated", "postsUpdated", "postsSkipped",
			"mediaCreated", "keywordsLinked"
	);

	@Around("@within(org.springframework.stereotype.Service) && execution(public * *(..))")
	public Object logServiceOperation(ProceedingJoinPoint joinPoint) throws Throwable {
		MethodSignature signature = (MethodSignature) joinPoint.getSignature();
		String service = signature.getDeclaringType().getSimpleName();
		String method = signature.getName();
		String arguments = summarizeArguments(signature.getParameterNames(), joinPoint.getArgs());
		long startedAt = System.nanoTime();

		log.info("Service operation started: service={}, method={}, arguments={}", service, method, arguments);
		try {
			Object result = joinPoint.proceed();
			log.info(
					"Service operation completed: service={}, method={}, result={}, durationMs={}",
					service,
					method,
					signature.getReturnType() == Void.TYPE ? "void" : summarizeValue(result),
					elapsedMillis(startedAt)
			);
			return result;
		} catch (Throwable exception) {
			String message = safeExceptionMessage(exception);
			if (isExpectedApplicationException(exception)) {
				log.warn(
						"Service operation rejected: service={}, method={}, exception={}, message={}, durationMs={}",
						service,
						method,
						exception.getClass().getSimpleName(),
						message,
						elapsedMillis(startedAt)
				);
			} else {
				log.error(
						"Service operation failed: service={}, method={}, exception={}, message={}, durationMs={}",
						service,
						method,
						exception.getClass().getSimpleName(),
						message,
						elapsedMillis(startedAt),
						exception
				);
			}
			throw exception;
		}
	}

	static String summarizeArguments(String[] parameterNames, Object[] arguments) {
		if (arguments == null || arguments.length == 0) {
			return "none";
		}
		return IntStream.range(0, arguments.length)
				.mapToObj(index -> parameterName(parameterNames, index) + "=" + summarizeValue(arguments[index]))
				.reduce((left, right) -> left + ", " + right)
				.orElse("none");
	}

	static String summarizeValue(Object value) {
		if (value == null) {
			return "null";
		}
		if (value instanceof CharSequence sequence) {
			return "<redacted-string length=" + sequence.length() + ">";
		}
		if (value instanceof Number
				|| value instanceof Boolean
				|| value instanceof Enum<?>
				|| value instanceof UUID
				|| value instanceof TemporalAccessor) {
			return String.valueOf(value);
		}
		if (value instanceof Pageable pageable) {
			return "Pageable[page=" + pageable.getPageNumber()
					+ ", size=" + pageable.getPageSize()
					+ ", sort=" + pageable.getSort() + "]";
		}
		if (value instanceof PageResponseDTO<?> page) {
			return "PageResponseDTO[page=" + page.page()
					+ ", size=" + page.size()
					+ ", elements=" + page.content().size()
					+ ", totalElements=" + page.totalElements() + "]";
		}
		if (value instanceof Optional<?> optional) {
			return optional.map(item -> "Optional[" + summarizeValue(item) + "]").orElse("Optional.empty");
		}
		if (value instanceof Collection<?> collection) {
			return value.getClass().getSimpleName() + "[size=" + collection.size() + "]";
		}
		if (value instanceof Map<?, ?> map) {
			return value.getClass().getSimpleName() + "[size=" + map.size() + "]";
		}
		if (value.getClass().isArray()) {
			return value.getClass().getComponentType().getSimpleName() + "[length=" + Array.getLength(value) + "]";
		}
		if (value instanceof Source source) {
			return "Source[id=" + source.getId() + ", code=" + source.getCode() + "]";
		}
		if (value instanceof Keyword keyword) {
			return "Keyword[id=" + keyword.getId() + ", language=" + keyword.getLanguage() + "]";
		}
		if (value instanceof ScrapeJob scrapeJob) {
			return "ScrapeJob[id=" + scrapeJob.getId() + ", status=" + scrapeJob.getStatus() + "]";
		}
		if (value instanceof Post post) {
			return "Post[id=" + post.getId() + "]";
		}
		if (value instanceof Author author) {
			return "Author[id=" + author.getId() + "]";
		}
		if (value.getClass().isRecord()) {
			return summarizeRecord(value);
		}
		return value.getClass().getSimpleName();
	}

	private static String summarizeRecord(Object value) {
		String details = java.util.Arrays.stream(value.getClass().getRecordComponents())
				.filter(component -> SAFE_RECORD_COMPONENTS.contains(component.getName()))
				.map(component -> component.getName() + "=" + readRecordComponent(component, value))
				.reduce((left, right) -> left + ", " + right)
				.orElse("");
		return value.getClass().getSimpleName() + (details.isEmpty() ? "" : "[" + details + "]");
	}

	private static String readRecordComponent(RecordComponent component, Object value) {
		try {
			return summarizeValue(component.getAccessor().invoke(value));
		} catch (ReflectiveOperationException exception) {
			return "<unavailable>";
		}
	}

	private static String parameterName(String[] parameterNames, int index) {
		if (parameterNames == null || index >= parameterNames.length) {
			return "arg" + index;
		}
		return parameterNames[index];
	}

	private boolean isExpectedApplicationException(Throwable exception) {
		Package exceptionPackage = exception.getClass().getPackage();
		return exceptionPackage != null
				&& exceptionPackage.getName().equals("org.raul.javawebscarper.exception");
	}

	private String safeExceptionMessage(Throwable exception) {
		String message = exception.getMessage();
		if (message == null || message.isBlank()) {
			return "<no-message>";
		}
		return message.replace('\n', ' ').replace('\r', ' ');
	}

	private long elapsedMillis(long startedAt) {
		return (System.nanoTime() - startedAt) / 1_000_000;
	}
}
