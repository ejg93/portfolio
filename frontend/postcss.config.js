// Turbopack(Next 16 기본 번들러)의 postcss 워커는 플러그인 이름 문자열을 제 위치에서 못 찾는다.
// 여기서 직접 require 해서 해석을 이 파일 기준으로 고정한다.
module.exports = {
  plugins: [require('@tailwindcss/postcss')],
}
