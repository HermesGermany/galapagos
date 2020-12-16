package com.hermesworld.ais.galapagos.topics.impl;

import java.util.Locale;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.hermesworld.ais.galapagos.applications.ApplicationMetadata;
import com.hermesworld.ais.galapagos.applications.BusinessCapability;
import com.hermesworld.ais.galapagos.applications.KnownApplication;
import com.hermesworld.ais.galapagos.topics.InvalidTopicNameException;
import com.hermesworld.ais.galapagos.topics.TopicNameValidator;
import com.hermesworld.ais.galapagos.topics.TopicType;
import com.hermesworld.ais.galapagos.util.CertificateUtil;

/**
 * Default Topic Name strategy. Enforces the following rules: <br>
 * <br>
 * For <code>EVENT, DATA, COMMAND</code> topics:
 * <ul>
 * <li>Topic Name must start with the prefix configured in <code>${galapagos.topics.namePrefix}</code></li>
 * <li>After the prefix, the value of <code>${galapagos.topics.nameSeparator}</code> (default: <code>.</code>) must
 * follow</li>
 * <li>After the separator, the lowercase topic type must follow, followed by another separator</li>
 * <li>After this separator, the name of one of the business capabilities of the owner applications must follow,
 * transformed to lower case, all non-alphanumeric characters replaced with a hyphen (<code>-</code>), consecutive
 * hyphens replaced by a single one, leading and trailing hyphens removed</li>
 * <li>After the business capability, another separator must follow</li>
 * <li>After this separator, an arbitrary number of sections may follow (at least one), each separated with another
 * separator</li>
 * <li>Every section must only consist of lowercase characters, hyphens, and numbers, and not begin or end with a
 * hyphen, and must not contain two consecutive hyphens, and must not consist only of numbers.</li>
 * <li>The last section may alternatively be a PascalCase word, so it may start with an uppercase character and contain
 * additional ones (but not only uppercase characters), but in this case, it must not contain hyphens</li>
 * <li>The whole topic name must not contain consecutive separators, and must not end with a separator.</li>
 * </ul>
 * <br>
 * For <code>INTERNAL</code> topics:<br>
 * <br>
 * <ul>
 * <li>Topic name must start with the topic prefix configured in the given application metadata. The rules from above
 * apply for additional sections in the name.</li>
 * </ul>
 *
 * @author AlbrechtFlo
 *
 */
@Component
public class DefaultTopicNameValidator implements TopicNameValidator {

	private String topicNamePrefix;

	private String topicNameSeparator;

	private static final String INVALID_TOPIC_NAME = "The topic name is invalid";

	@Autowired
	public DefaultTopicNameValidator(@Value("${galapagos.topics.namePrefix:topics.}") String topicNamePrefix,
			@Value("${galapagos.topics.nameSeparator:.}") String topicNameSeparator) {
		this.topicNamePrefix = topicNamePrefix;
		this.topicNameSeparator = topicNameSeparator;
	}

	@Override
	public void validateTopicName(String topicName, TopicType topicType, KnownApplication ownerApplication,
			ApplicationMetadata applicationMetadata)
			throws InvalidTopicNameException {
		if (topicType == TopicType.INTERNAL) {
			validateInternalTopicName(topicName, ownerApplication, applicationMetadata);
		}
		else {
			ownerApplication.getBusinessCapabilities().stream().filter(bc -> topicName.startsWith(topicPrefix(bc, topicType))).findAny()
					.orElseThrow(() -> new InvalidTopicNameException("The topic name is not valid for this application"));

			validateSections(topicName);
		}
	}

	protected void validateInternalTopicName(String topicName, KnownApplication ownerApplication,
			ApplicationMetadata applicationMetadata)
			throws InvalidTopicNameException {
		if (!topicName.startsWith(applicationMetadata.getTopicPrefix())) {
			throw new InvalidTopicNameException("The topic name must start with " + applicationMetadata.getTopicPrefix());
		}

		validateSections(topicName);
	}

	protected void validateSections(String topicName) throws InvalidTopicNameException {
		String[] sections = topicName.split(Pattern.quote(topicNameSeparator), -1);

		for (int i = 0; i < sections.length; i++) {
			String section = sections[i];
			if (StringUtils.isEmpty(section)) {
				throw new InvalidTopicNameException(INVALID_TOPIC_NAME);
			}

			if (section.matches("[0-9]+")) {
				throw new InvalidTopicNameException("The topic name is invalid: No name section must consist only of numbers");
			}

			if (!section.matches("[a-z0-9\\-]+")) {
				// last one may be in PascalCase
				if (i != sections.length - 1 || !isPascalCase(section)) {
					throw new InvalidTopicNameException(INVALID_TOPIC_NAME);
				}
			}

			if (section.startsWith("-") || section.endsWith("-") || section.contains("--")) {
				throw new InvalidTopicNameException(INVALID_TOPIC_NAME);
			}
		}
	}

	@Override
	public String getTopicNameSuggestion(TopicType topicType, KnownApplication ownerApplication,
			ApplicationMetadata applicationMetadata,
			BusinessCapability businessCapability) {
		if (topicType == TopicType.INTERNAL) {
			return applicationMetadata.getTopicPrefix() + "my-topic";
		}

		if (businessCapability == null) {
			return null;
		}

		return topicPrefix(businessCapability, topicType) + "my-topic";
	}

	private String topicPrefix(BusinessCapability businessCapability, TopicType topicType) {
		StringBuilder prefix = new StringBuilder(this.topicNamePrefix);
		prefix.append(this.topicNameSeparator);
		prefix.append(topicType.name().toLowerCase(Locale.US));
		prefix.append(this.topicNameSeparator);
		prefix.append(toTopicNamePart(businessCapability.getName()));
		prefix.append(this.topicNameSeparator);
		return prefix.toString();
	}

	private static String toTopicNamePart(String name) {
		return CertificateUtil.toAppCn(name).replace('_', '-');
	}

	private static boolean isPascalCase(String str) {
		if (!Character.isUpperCase(str.charAt(0))) {
			return false;
		}
		if (str.length() == 1) {
			// we allow this corner case for now
			return true;
		}

		if (!str.matches("[a-zA-Z0-9]+")) {
			return false;
		}

		for (int i = 0; i < str.length(); i++) {
			if (Character.isLowerCase(str.charAt(i))) {
				return true;
			}
		}

		return false;
	}

}
