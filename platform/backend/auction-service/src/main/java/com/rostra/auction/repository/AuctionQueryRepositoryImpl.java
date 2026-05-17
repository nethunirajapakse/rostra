package com.rostra.auction.repository;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.rostra.auction.entity.Auction;
import com.rostra.auction.entity.QAuction;
import jakarta.persistence.EntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.List;

public class AuctionQueryRepositoryImpl implements AuctionQueryRepository {

    private final JPAQueryFactory queryFactory;

    public AuctionQueryRepositoryImpl(EntityManager em) {
        this.queryFactory = new JPAQueryFactory(em);
    }

    @Override
    public Page<Auction> search(AuctionSearchCriteria criteria, Pageable pageable) {
        QAuction a = QAuction.auction;
        BooleanBuilder where = buildPredicate(criteria, a);

        JPAQuery<Auction> dataQuery = queryFactory
                .selectFrom(a)
                .where(where);

        for (Sort.Order order : pageable.getSort()) {
            var path = Expressions.stringPath(a, order.getProperty());
            dataQuery.orderBy(order.isAscending() ? path.asc() : path.desc());
        }

        List<Auction> rows = dataQuery
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        Long total = queryFactory
                .select(a.count())
                .from(a)
                .where(where)
                .fetchOne();

        return new PageImpl<>(rows, pageable, total != null ? total : 0L);
    }

    private BooleanBuilder buildPredicate(AuctionSearchCriteria criteria, QAuction a) {
        BooleanBuilder where = new BooleanBuilder();
        if (criteria.status() != null)        where.and(a.status.eq(criteria.status()));
        if (criteria.sellerId() != null)      where.and(a.sellerId.eq(criteria.sellerId()));
        if (criteria.minPrice() != null)      where.and(a.currentPrice.goe(criteria.minPrice()));
        if (criteria.maxPrice() != null)      where.and(a.currentPrice.loe(criteria.maxPrice()));
        if (criteria.endingBefore() != null)  where.and(a.endsAt.loe(criteria.endingBefore()));
        if (criteria.search() != null && !criteria.search().isBlank()) {
            where.and(a.title.containsIgnoreCase(criteria.search().trim()));
        }
        return where;
    }
}
