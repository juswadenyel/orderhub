package edu.cit.dingding.channel;

import org.springframework.data.jpa.repository.JpaRepository;

interface ChannelEventRepository extends JpaRepository<ChannelEventRecord, String> {
}
