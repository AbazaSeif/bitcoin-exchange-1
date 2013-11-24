package at.outdated.bitcoin.exchange.icbit;

import at.outdated.bitcoin.exchange.api.client.ExchangeApiClient;
import at.outdated.bitcoin.exchange.api.market.Market;
import at.outdated.bitcoin.exchange.api.currency.Currency;

/**
 * Created by ebirn on 31.10.13.
 */
public class IcbitMarket  extends Market {

    public IcbitMarket() {
        super("icbit", "https://icbit.se", "icbit.se");
    }

    @Override
    public ExchangeApiClient getApiClient() {
        return new IcbitClient(this);
    }
}