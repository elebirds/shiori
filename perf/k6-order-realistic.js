import { sleep } from 'k6';

import {
  buildOptions,
  collectBuyerCdkCodes,
  createPublishedProduct,
  ensureGatewayHealth,
  executeOrderLifecycle,
  perfPrefix,
  redeemCodeList,
  registerAndLoginUser,
} from './k6-order-common.js';

const scriptTag = 'k6-order-realistic';
const buyerCount = parsePositiveInt('K6_ORDER_REAL_BUYERS', __ENV.K6_ORDER_REAL_BUYERS || 80);
const sellerCount = parsePositiveInt('K6_ORDER_REAL_SELLERS', __ENV.K6_ORDER_REAL_SELLERS || 20);
const productsPerSeller = parsePositiveInt(
  'K6_ORDER_REAL_PRODUCTS_PER_SELLER',
  __ENV.K6_ORDER_REAL_PRODUCTS_PER_SELLER || 2,
);

export const options = buildOptions();

function parsePositiveInt(name, rawValue) {
  const value = Number(rawValue);
  if (!Number.isInteger(value) || value < 1) {
    throw new Error(`${name} must be a positive integer, got=${rawValue}`);
  }
  return value;
}

function pickIndex(length, salt) {
  return (((__ITER * salt) + (__VU - 1)) % length + length) % length;
}

export function setup() {
  ensureGatewayHealth();

  const cdkCodes = collectBuyerCdkCodes();
  if (cdkCodes.length > 0 && cdkCodes.length < buyerCount) {
    throw new Error(`K6_ORDER_BUYER_CDKS count is insufficient, need>=${buyerCount}, got=${cdkCodes.length}`);
  }

  const buyers = [];
  for (let buyerIndex = 0; buyerIndex < buyerCount; buyerIndex += 1) {
    const buyer = registerAndLoginUser(
      `${perfPrefix}_buyer_${buyerIndex + 1}`,
      `Buyer ${perfPrefix} ${buyerIndex + 1}`,
    );
    if (cdkCodes[buyerIndex]) {
      redeemCodeList(buyer.accessToken, [cdkCodes[buyerIndex]]);
    }
    buyers.push({
      buyerIndex,
      buyerToken: buyer.accessToken,
      buyerUserId: buyer.userId,
    });
  }

  const offers = [];
  for (let sellerIndex = 0; sellerIndex < sellerCount; sellerIndex += 1) {
    const seller = registerAndLoginUser(
      `${perfPrefix}_seller_${sellerIndex + 1}`,
      `Seller ${perfPrefix} ${sellerIndex + 1}`,
    );
    for (let productIndex = 0; productIndex < productsPerSeller; productIndex += 1) {
      const product = createPublishedProduct(seller.accessToken, {
        title: `Perf 商品 ${perfPrefix} S${sellerIndex + 1}P${productIndex + 1}`,
        description: 'k6 realistic order flow',
        skus: [
          {
            specItems: [{ name: '批次', value: `S${sellerIndex + 1}-P${productIndex + 1}` }],
            priceCent: 1,
            stock: 200000,
          },
        ],
      });
      offers.push({
        offerIndex: offers.length,
        sellerIndex,
        productIndex,
        sellerToken: seller.accessToken,
        sellerUserId: seller.userId,
        productId: product.productId,
        skuId: product.skus[0].skuId,
      });
    }
  }

  return { buyers, offers };
}

export default function (data) {
  const buyer = data.buyers[pickIndex(data.buyers.length, 17)];
  const offer = data.offers[pickIndex(data.offers.length, 31)];

  executeOrderLifecycle({
    buyerToken: buyer.buyerToken,
    sellerToken: offer.sellerToken,
    productId: offer.productId,
    skuId: offer.skuId,
    buyerIndex: buyer.buyerIndex,
    offerIndex: offer.offerIndex,
    flowPrefix: `${perfPrefix}-realistic-b${buyer.buyerIndex + 1}-o${offer.offerIndex + 1}`,
    scriptTag,
  });

  sleep(0.2);
}
