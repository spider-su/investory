package com.smartbox.investory.investment.infrastructure.persistence.portfolio;

import jakarta.persistence.EntityManager;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class PortfolioDataQualityRepository {
  private final EntityManager entityManager;

  @SuppressWarnings("unchecked")
  public List<Object[]> findSnapshot(Long portfolioId) {
    return entityManager
        .createNativeQuery(
            "WITH p AS (SELECT COUNT(*) total, "
                + "COUNT(*) FILTER (WHERE a.market_price IS NOT NULL) priced, "
                + "COUNT(*) FILTER (WHERE a.market_price IS NULL) missing "
                + "FROM investory.positions x JOIN investory.accounts xa ON xa.id=x.account_id LEFT JOIN investory.assets a ON a.id=x.asset_id WHERE xa.portfolio_id=:portfolioId AND x.close_time IS NULL AND COALESCE(x.volume,0)<>0), "
                + "s AS (SELECT COUNT(*) total_accounts, COUNT(*) FILTER (WHERE account_id IS NOT NULL) reconciled FROM investory.app_v_account_statistics WHERE portfolio_id=:portfolioId), "
                + "d AS (SELECT MAX(price_updated_at) latest_price FROM investory.assets), "
                + "f AS (SELECT MAX(rate_date) latest_fx FROM investory.exchange_rates), "
                + "i AS (SELECT MAX(finished_at) latest_import FROM investory.import_history WHERE status='COMPLETED' AND portfolio_id=:portfolioId), "
                + "r AS (SELECT MAX(ad.updated_at) latest_refresh FROM investory.account_daily ad JOIN investory.accounts aa ON aa.id=ad.account_id WHERE aa.portfolio_id=:portfolioId) "
                + "SELECT CASE WHEN p.missing>0 OR s.reconciled<s.total_accounts THEN 'CRITICAL' WHEN p.total>0 AND p.priced<p.total THEN 'REVIEW' ELSE 'HEALTHY' END, "
                + "s.reconciled,s.total_accounts,p.priced,p.total,p.missing,0,0,0,0,0,0,0,i.latest_import,i.latest_import,d.latest_price,f.latest_fx,r.latest_refresh FROM p CROSS JOIN s CROSS JOIN d CROSS JOIN f CROSS JOIN i CROSS JOIN r")
        .setParameter("portfolioId", portfolioId)
        .getResultList();
  }

  @SuppressWarnings("unchecked")
  public List<Object[]> findIssues(Long portfolioId) {
    return entityManager
        .createNativeQuery(
            "SELECT issue_type, account_id, asset_id, issue_code, price_age_days, "
                + "selected_price_date, price_currency, price_quality, price_origin, reconstruction_status, selected_price_history_id "
                + "FROM investory.recon_v_portfolio_data_quality_issue WHERE portfolio_id=:portfolioId ORDER BY issue_code, account_id, asset_id LIMIT 100")
        .setParameter("portfolioId", portfolioId)
        .getResultList();
  }
}
