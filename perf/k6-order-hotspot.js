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

const scriptTag = 'k6-order-hotspot';

export const options = buildOptions();

export function setup() {
  ensureGatewayHealth();

  const seller = registerAndLoginUser(`${perfPrefix}_seller`, `Seller ${perfPrefix}`);
  const buyer = registerAndLoginUser(`${perfPrefix}_buyer`, `Buyer ${perfPrefix}`);

  redeemCodeList(buyer.accessToken, collectBuyerCdkCodes());

  const product = createPublishedProduct(seller.accessToken, {
    title: `Perf 商品 ${perfPrefix}`,
    description: 'k6 hotspot order flow',
    skus: [
      { specItems: [{ name: '版本', value: '标准版' }], priceCent: 1, stock: 200000 },
      { specItems: [{ name: '版本', value: '豪华版' }], priceCent: 1, stock: 200000 },
    ],
  });

  return {
    sellerToken: seller.accessToken,
    sellerUserId: seller.userId,
    buyerToken: buyer.accessToken,
    buyerUserId: buyer.userId,
    productId: product.productId,
    skuId1: product.skus[0].skuId,
    skuId2: product.skus[1].skuId,
  };
}

export default function (data) {
  executeOrderLifecycle({
    buyerToken: data.buyerToken,
    sellerToken: data.sellerToken,
    productId: data.productId,
    skuId: data.skuId1,
    buyerIndex: 0,
    offerIndex: 0,
    flowPrefix: `${perfPrefix}-hotspot`,
    scriptTag,
  });

  sleep(0.2);
}
