package com.hermesworld.ais.galapagos.naming;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public interface ApplicationPrefixes {

    ApplicationPrefixes EMPTY = new ApplicationPrefixes() {
        @Override
        public List<String> getInternalTopicPrefixes() {
            return List.of();
        }

        @Override
        public List<String> getConsumerGroupPrefixes() {
            return List.of();
        }

        @Override
        public List<String> getTransactionIdPrefixes() {
            return List.of();
        }
    };

    List<String> getInternalTopicPrefixes();

    List<String> getConsumerGroupPrefixes();

    List<String> getTransactionIdPrefixes();

    /**
     * Combines the prefixes set in this object with the prefixes set in the given other object, returning the "union"
     * of both, but removing duplicate prefixes.
     *
     * @param other Other set of prefixes to combine with this set.
     * @return A "union" of both sets of prefixes, without duplicates in its lists.
     */
    default ApplicationPrefixes combineWith(ApplicationPrefixes other) {
        ApplicationPrefixes thisObj = this;

        return new ApplicationPrefixes() {
            @Override
            public List<String> getInternalTopicPrefixes() {
                return Stream
                        .concat(thisObj.getInternalTopicPrefixes().stream(), other.getInternalTopicPrefixes().stream())
                        .distinct().collect(Collectors.toList());
            }

            @Override
            public List<String> getConsumerGroupPrefixes() {
                return Stream
                        .concat(thisObj.getConsumerGroupPrefixes().stream(), other.getConsumerGroupPrefixes().stream())
                        .distinct().collect(Collectors.toList());
            }

            @Override
            public List<String> getTransactionIdPrefixes() {
                return Stream
                        .concat(thisObj.getTransactionIdPrefixes().stream(), other.getTransactionIdPrefixes().stream())
                        .distinct().collect(Collectors.toList());
            }
        };
    }

}
