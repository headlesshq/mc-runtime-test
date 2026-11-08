{
  description = "Development shell for mc-runtime-test";

  inputs.nixpkgs.url = "github:NixOS/nixpkgs/nixos-26.05";

  outputs =
    { nixpkgs, ... }:
    let
      inherit (nixpkgs) lib;

      systems = [
        "x86_64-linux"
        "aarch64-linux"
        "aarch64-darwin"
      ];

      forAllSystems = f: lib.genAttrs systems (system: f nixpkgs.legacyPackages.${system});
    in
    {
      devShells = forAllSystems (
        pkgs:
        let
          jdk = pkgs.temurin-bin-25;
        in
        {
          default = pkgs.mkShell {
            packages = [
              jdk
              pkgs.gh
              pkgs.shellcheck
              pkgs.actionlint
              pkgs.yq-go
              pkgs.act
            ];

            JAVA_HOME = jdk.home;
          };
        }
      );

      formatter = forAllSystems (pkgs: pkgs.nixfmt);
    };
}
