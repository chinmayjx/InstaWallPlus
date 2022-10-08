const fs = require("fs");
const out = "saved_posts.json";
let sp = [];
function save(resJSON) {
  sp = sp.concat(resJSON.edges);
	fs.writeFileSync(out, JSON.stringify(sp, null, "  "));
}

function get(cur) {
	console.log(sp.length);
  fetch(
    `https://www.instagram.com/graphql/query/?query_hash=2ce1d673055b99250e93b6f88f878fde&variables={\"id\":\"1240220703\",\"first\":12,\"after\":\"${cur}\"}`,

    {
      headers: {
        accept: "*/*",
        "accept-language": "en-US,en;q=0.9",
        "sec-ch-prefers-color-scheme": "light",
        "sec-ch-ua":
          '"Chromium";v="106", "Google Chrome";v="106", "Not;A=Brand";v="99"',
        "sec-ch-ua-mobile": "?0",
        "sec-ch-ua-platform": '"Linux"',
        "sec-fetch-dest": "empty",
        "sec-fetch-mode": "cors",
        "sec-fetch-site": "same-origin",
        "viewport-width": "960",
        "x-asbd-id": "198387",
        "x-csrftoken": "ZRFPZzJFUqeZQczlEN7FE07cB93TSCYl",
        "x-ig-app-id": "936619743392459",
        "x-ig-www-claim":
          "hmac.AR1VS1Oy24B-E9z2f8Q6p8yp7Xs0-GGcq4TFGdHbacxOmK6N",
        "x-instagram-ajax": "1006354674",
        "x-requested-with": "XMLHttpRequest",
        cookie:
          'ig_did=2275A8EC-2E2E-4359-B294-F454A392AFBC; ig_nrcb=1; mid=Y0D87QAEAAGVm7ia223gR4isPzqv; shbid="14944\\0541240220703\\0541696739478:01f73c29abb680eea7442858862d1a08074fe17c50210e278c9393ce19f4f58d1e6d0ae4"; shbts="1665203478\\0541240220703\\0541696739478:01f7ab05690cc29d0bc82c31ad268f2e177c53fa35af600b41cde18d4d270b54ee5b3fd0"; datr=5ABBY8KvRxLZpRe3cgZSrlPN; csrftoken=ZRFPZzJFUqeZQczlEN7FE07cB93TSCYl; sessionid=1240220703%3AUikzLUiLm2xwtq%3A10%3AAYfFfmMNcLWTN92yCOkNPpcxEWIM4a5JbszU0zboiw; ds_user_id=1240220703; rur="RVA\\0541240220703\\0541696743345:01f7521a215d019028274b31415e11fd22b557318e32c6661a826bce4103793d167ad3a1"',
        Referer: "https://www.instagram.com/chinmayjain08/saved/",
        "Referrer-Policy": "strict-origin-when-cross-origin",
      },
      body: null,
      method: "GET",
    }
  ).then(async (res) => {
		let resJSON = (await res.json()).data.user.edge_saved_media;
		save(resJSON);
		if(resJSON.page_info.has_next_page){
			get(resJSON.page_info.end_cursor)
		}
		// console.log(resJSON);
	});
}

fetch(
  "https://i.instagram.com/api/v1/users/web_profile_info/?username=chinmayjain08",
  {
    headers: {
      accept: "*/*",
      "accept-language": "en-US,en;q=0.9",
      "sec-ch-ua":
        '"Chromium";v="106", "Google Chrome";v="106", "Not;A=Brand";v="99"',
      "sec-ch-ua-mobile": "?0",
      "sec-ch-ua-platform": '"Linux"',
      "sec-fetch-dest": "empty",
      "sec-fetch-mode": "cors",
      "sec-fetch-site": "same-site",
      "x-asbd-id": "198387",
      "x-csrftoken": "ZRFPZzJFUqeZQczlEN7FE07cB93TSCYl",
      "x-ig-app-id": "936619743392459",
      "x-ig-www-claim": "hmac.AR1VS1Oy24B-E9z2f8Q6p8yp7Xs0-GGcq4TFGdHbacxOmK6N",
      "x-instagram-ajax": "1006354674",
      cookie:
        'ig_did=2275A8EC-2E2E-4359-B294-F454A392AFBC; ig_nrcb=1; mid=Y0D87QAEAAGVm7ia223gR4isPzqv; shbid="14944\\0541240220703\\0541696739478:01f73c29abb680eea7442858862d1a08074fe17c50210e278c9393ce19f4f58d1e6d0ae4"; shbts="1665203478\\0541240220703\\0541696739478:01f7ab05690cc29d0bc82c31ad268f2e177c53fa35af600b41cde18d4d270b54ee5b3fd0"; datr=5ABBY8KvRxLZpRe3cgZSrlPN; csrftoken=ZRFPZzJFUqeZQczlEN7FE07cB93TSCYl; sessionid=1240220703%3AUikzLUiLm2xwtq%3A10%3AAYfFfmMNcLWTN92yCOkNPpcxEWIM4a5JbszU0zboiw; ds_user_id=1240220703; rur="RVA\\0541240220703\\0541696743345:01f7521a215d019028274b31415e11fd22b557318e32c6661a826bce4103793d167ad3a1"',
      Referer: "https://www.instagram.com/",
      "Referrer-Policy": "strict-origin-when-cross-origin",
    },
    body: null,
    method: "GET",
  }
).then(async (res) => {
	let resJSON = (await res.json()).data.user.edge_saved_media;
  save(resJSON);
  get(resJSON.page_info.end_cursor);
});
