package com.chatservice.marketplace.conversation.service;

import java.time.Instant;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.chatservice.marketplace.common.PartyRole;
import com.chatservice.marketplace.common.error.BusinessException;
import com.chatservice.marketplace.common.error.ErrorCode;
import com.chatservice.marketplace.common.member.MemberDirectory;
import com.chatservice.marketplace.common.time.TimeRules;
import com.chatservice.marketplace.conversation.ConversationRepository;
import com.chatservice.marketplace.conversation.domain.Conversation;
import com.chatservice.marketplace.conversation.domain.ConversationWritability;
import com.chatservice.marketplace.conversation.dto.ConversationDetailResponse;
import com.chatservice.marketplace.conversation.dto.ConversationSummaryResponse;
import com.chatservice.marketplace.conversation.realtime.RealtimePublisher;
import com.chatservice.marketplace.conversation.realtime.event.ConversationStateEvent;
import com.chatservice.marketplace.offer.PriceOfferRepository;
import com.chatservice.marketplace.order.PurchaseOrderRepository;
import com.chatservice.marketplace.order.domain.PurchaseOrder;
import com.chatservice.marketplace.product.domain.Product;
import com.chatservice.marketplace.product.service.IProductService;

@Service
public class ConversationService implements IConversationService {

    private static final Logger logger = LoggerFactory.getLogger(ConversationService.class);

    private final ConversationRepository conversationRepository;
    private final PriceOfferRepository priceOfferRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final IProductService productService;
    private final MemberDirectory memberDirectory;
    private final RealtimePublisher realtimePublisher;
    private final TimeRules timeRules;

    public ConversationService(ConversationRepository conversationRepository,
                               PriceOfferRepository priceOfferRepository,
                               PurchaseOrderRepository purchaseOrderRepository,
                               IProductService productService,
                               MemberDirectory memberDirectory,
                               RealtimePublisher realtimePublisher,
                               TimeRules timeRules) {
        this.conversationRepository = conversationRepository;
        this.priceOfferRepository = priceOfferRepository;
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.productService = productService;
        this.memberDirectory = memberDirectory;
        this.realtimePublisher = realtimePublisher;
        this.timeRules = timeRules;
    }

    /*
     * 처리 순서(설계 6.5): 상품 확인(404) → 본인 상품 거절(403) → 기존 대화가 있으면 판매 상태와 관계없이 반환 →
     * 없으면 판매 중일 때만 새로 만든다(409). 같은 조합의 동시 시작을 하나로 만드는 처리는 후속 과제다.
     */
    @Override
    @Transactional
    public StartResult start(String memberId, Long productId) {
        Product product = productService.getProduct(productId);
        if (product.isSoldBy(memberId)) {
            throw new BusinessException(ErrorCode.SELF_TRADE_NOT_ALLOWED);
        }
        Optional<Conversation> existing = conversationRepository.findByProductIdAndBuyerId(productId, memberId);
        if (existing.isPresent()) {
            return new StartResult(false, buildDetail(existing.get(), product, memberId));
        }
        if (!product.isOnSale()) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_ON_SALE);
        }
        Conversation conversation = conversationRepository.save(
                Conversation.open(productId, memberId, product.getSellerId(), false, timeRules.now()));
        logger.info("[대화 생성] conversationId={}, productId={}, memberId={}",
                conversation.getConversationId(), productId, memberId);
        return new StartResult(true, buildDetail(conversation, product, memberId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ConversationSummaryResponse> list(String memberId) {
        List<Conversation> conversations = conversationRepository.findAllForMember(memberId);
        Set<String> memberIds = new HashSet<>();
        conversations.forEach(c -> {
            memberIds.add(c.getBuyerId());
            memberIds.add(c.getSellerId());
        });
        Map<String, String> nicknames = memberDirectory.nicknames(memberIds);
        return conversations.stream().map(conversation -> {
            Product product = productService.getProduct(conversation.getProductId());
            boolean buyer = conversation.isBuyer(memberId);
            String counterpart = buyer ? conversation.getSellerId() : conversation.getBuyerId();
            return new ConversationSummaryResponse(conversation.getConversationId(),
                    ConversationSummaryResponse.ProductBrief.of(product),
                    buyer ? PartyRole.BUYER : PartyRole.SELLER,
                    nicknames.get(counterpart),
                    writability(conversation, product).writable(),
                    conversation.getCreatedAt());
        }).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ConversationDetailResponse detail(String memberId, Long conversationId) {
        Conversation conversation = getForMember(memberId, conversationId);
        Product product = productService.getProduct(conversation.getProductId());
        return buildDetail(conversation, product, memberId);
    }

    @Override
    @Transactional(readOnly = true)
    public Conversation getForMember(String memberId, Long conversationId) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CONVERSATION_NOT_FOUND));
        if (!conversation.isParticipant(memberId)) {
            throw new BusinessException(ErrorCode.NOT_CONVERSATION_MEMBER);
        }
        return conversation;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isParticipant(Long conversationId, String memberId) {
        return conversationRepository.findById(conversationId)
                .map(conversation -> conversation.isParticipant(memberId))
                .orElse(false);
    }

    @Override
    @Transactional(readOnly = true)
    public ConversationWritability writability(Conversation conversation) {
        return writability(conversation, productService.getProduct(conversation.getProductId()));
    }

    @Override
    @Transactional
    public void ensureTradeConversation(Product product, String buyerId, Instant now) {
        if (conversationRepository.findByProductIdAndBuyerId(product.getProductId(), buyerId).isPresent()) {
            return;
        }
        Conversation conversation = conversationRepository.save(
                Conversation.open(product.getProductId(), buyerId, product.getSellerId(), true, now));
        logger.info("[대화 생성] 결제 시 시스템 생성 conversationId={}, productId={}, memberId={}",
                conversation.getConversationId(), product.getProductId(), buyerId);
    }

    @Override
    @Transactional(readOnly = true)
    public void publishStateToProductConversations(Product product) {
        Map<Long, ConversationStateEvent> events = new LinkedHashMap<>();
        for (Conversation conversation : conversationRepository.findByProductIdOrderByConversationIdAsc(product.getProductId())) {
            events.put(conversation.getConversationId(), stateEvent(conversation, product));
        }
        realtimePublisher.publishToProductConversations(product.getProductId(), events);
    }

    /**
     * 주문이 최종 상태가 되었을 때 거래 당사자의 대화에 대화 상태 이벤트({@code CONVERSATION_STATE})를 보낸다.
     *
     * 처리 흐름: 상품·구매자로 대화 조회 → 상품 조회 → 쓰기 가능 여부 계산과 이벤트 생성 → {@link RealtimePublisher#publish} 호출.
     * 대화가 없으면 아무것도 하지 않는다. DB는 바꾸지 않으며, 실제 전송은 커밋 뒤에 실행된다.
     *
     * @param productId 주문의 상품 ID
     * @param buyerId   주문의 구매자 ID. 판매자는 상품에서 정해지므로 상품과 구매자로 대화 하나가 정해진다.
     */
    @Override
    @Transactional(readOnly = true)
    public void publishStateToTradeConversation(Long productId, String buyerId) {
        conversationRepository.findByProductIdAndBuyerId(productId, buyerId).ifPresent(conversation -> {
            Product product = productService.getProduct(productId);
            realtimePublisher.publish(conversation.getConversationId(), stateEvent(conversation, product), null);
        });
    }

    /**
     * 대화 하나의 상태 이벤트를 만든다.
     *
     * @param conversation 대상 대화
     * @param product      대화의 상품
     * @return 상품 상태, 남은 수량, 쓰기 가능 여부, 읽기 전용 사유를 담은 이벤트
     */
    private ConversationStateEvent stateEvent(Conversation conversation, Product product) {
        ConversationWritability writability = writability(conversation, product);
        return ConversationStateEvent.of(conversation.getConversationId(), product.getStatus().name(),
                product.getRemainingQuantity(), writability.writable(),
                writability.readOnlyReason() == null ? null : writability.readOnlyReason().name());
    }

    /**
     * 대화의 쓰기 가능 여부를 계산한다.
     *
     * 상품이 판매 중이면 쓰기 가능이다. 판매 종료이면 이 대화의 구매자·판매자 주문만 조회해,
     * 진행 중이거나 보류인 주문이 하나라도 있으면 쓰기 가능, 없으면 읽기 전용으로 판단한다.
     *
     * @param conversation 대상 대화
     * @param product      대화의 상품
     * @return 쓰기 가능 여부와 읽기 전용 사유
     */
    private ConversationWritability writability(Conversation conversation, Product product) {
        if (product.isOnSale()) {
            return ConversationWritability.evaluate(true, List.of());
        }
        List<PurchaseOrder> partyOrders = partyOrders(conversation);
        return ConversationWritability.evaluate(false, partyOrders.stream().map(PurchaseOrder::getTradeStatus).toList());
    }

    private List<PurchaseOrder> partyOrders(Conversation conversation) {
        return purchaseOrderRepository.findByProductIdAndBuyerIdAndSellerIdOrderByOrderIdDesc(
                conversation.getProductId(), conversation.getBuyerId(), conversation.getSellerId());
    }

    private ConversationDetailResponse buildDetail(Conversation conversation, Product product, String memberId) {
        Map<String, String> nicknames = memberDirectory.nicknames(Set.of(conversation.getBuyerId(), conversation.getSellerId()));
        ConversationWritability writability = writability(conversation, product);
        return new ConversationDetailResponse(
                conversation.getConversationId(),
                ConversationDetailResponse.ProductInfo.of(product),
                new ConversationDetailResponse.Party(nicknames.get(conversation.getBuyerId())),
                new ConversationDetailResponse.Party(nicknames.get(conversation.getSellerId())),
                conversation.isBuyer(memberId) ? PartyRole.BUYER : PartyRole.SELLER,
                writability.writable(),
                writability.readOnlyReason(),
                priceOfferRepository.findByConversationIdOrderByCreatedAtAscOfferIdAsc(conversation.getConversationId())
                        .stream().map(ConversationDetailResponse.OfferItem::of).toList(),
                partyOrders(conversation).stream().map(ConversationDetailResponse.OrderSummary::of).toList());
    }
}
